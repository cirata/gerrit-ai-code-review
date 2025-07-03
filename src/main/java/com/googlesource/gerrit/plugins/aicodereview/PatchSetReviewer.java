// Copyright (C) 2024 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.aicodereview;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.data.ChangeSetDataHandler;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.common.client.api.openapi.ChatAIClient;
import com.googlesource.gerrit.plugins.aicodereview.localization.Localizer;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritClient;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritClientReview;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.messages.DebugCodeBlocksReview;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.patch.comment.GerritCommentRange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.gerrit.GerritCodeRange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatReplyItem;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatResponseContent;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.review.ReviewBatch;
import com.googlesource.gerrit.plugins.aicodereview.settings.Settings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PatchSetReviewer {
  private static final String SPLIT_REVIEW_MSG =
      "Too many changes. Please consider splitting into patches smaller "
          + "than %s lines for review.";

  private final Configuration config;
  private final GerritClient gerritClient;
  private final ChangeSetData changeSetData;
  private final Provider<GerritClientReview> clientReviewProvider;
  @Getter private final ChatAIClient chatAIClient;
  private final Localizer localizer;
  private final DebugCodeBlocksReview debugCodeBlocksReview;

  private GerritCommentRange gerritCommentRange;
  private List<ReviewBatch> reviewBatches;
  private List<GerritComment> commentProperties;
  private List<Integer> reviewScores;

  @Inject
  PatchSetReviewer(
      GerritClient gerritClient,
      Configuration config,
      ChangeSetData changeSetData,
      Provider<GerritClientReview> clientReviewProvider,
      ChatAIClient chatAIClient,
      Localizer localizer) {
    this.config = config;
    this.gerritClient = gerritClient;
    this.changeSetData = changeSetData;
    this.clientReviewProvider = clientReviewProvider;
    this.chatAIClient = chatAIClient;
    this.localizer = localizer;
    debugCodeBlocksReview = new DebugCodeBlocksReview(localizer);
  }

  public void review(GerritChange change) throws Exception {
    reviewBatches = new ArrayList<>();
    reviewScores = new ArrayList<>();
    commentProperties = gerritClient.getClientData(change).getCommentProperties();
    gerritCommentRange = new GerritCommentRange(gerritClient, change);
    String patchSet = gerritClient.getPatchSet(change);
    if (patchSet.isEmpty() && config.getAIMode() == Settings.Modes.stateless) {
      log.info("No file to review has been found in the PatchSet");
      return;
    }
    ChangeSetDataHandler.update(config, change, gerritClient, changeSetData, localizer);

    if (changeSetData.shouldRequestAICodeReview()) {
      AIChatResponseContent reviewReply = getReviewReply(change, patchSet);
      log.debug("AIChat response: {}", reviewReply);

      retrieveReviewBatches(reviewReply, change);
    }
    clientReviewProvider
        .get()
        .setReview(change, reviewBatches, changeSetData, getReviewScore(change));
  }

  private void setCommentBatchMap(ReviewBatch batchMap, Integer batchID) {
    if (commentProperties != null && batchID < commentProperties.size()) {
      GerritComment commentProperty = commentProperties.get(batchID);
      if (commentProperty != null
          && (commentProperty.getLine() != null || commentProperty.getRange() != null)) {
        String id = commentProperty.getId();
        String filename = commentProperty.getFilename();
        Integer line = commentProperty.getLine();
        GerritCodeRange range = commentProperty.getRange();
        if (range != null) {
          batchMap.setId(id);
          batchMap.setFilename(filename);
          batchMap.setLine(line);
          batchMap.setRange(range);
        }
      }
    }
  }

  private void setPatchSetReviewBatchMap(ReviewBatch batchMap, AIChatReplyItem replyItem) {
    Optional<GerritCodeRange> optGerritCommentRange =
        gerritCommentRange.getGerritCommentRange(replyItem);
    if (optGerritCommentRange.isPresent()) {
      GerritCodeRange gerritCodeRange = optGerritCommentRange.get();
      batchMap.setFilename(replyItem.getFilename());
      batchMap.setLine(gerritCodeRange.getStartLine());
      batchMap.setRange(gerritCodeRange);
    }
  }

  private void retrieveReviewBatches(AIChatResponseContent reviewReply, GerritChange change) {
    if (reviewReply.getMessageContent() != null && !reviewReply.getMessageContent().isEmpty()) {
      reviewBatches.add(new ReviewBatch(reviewReply.getMessageContent()));
      return;
    }
    for (AIChatReplyItem replyItem : reviewReply.getReplies()) {
      String reply = replyItem.getReply();
      Integer score = replyItem.getScore();
      boolean isNotNegative = isNotNegativeReply(score);
      boolean isIrrelevant = isIrrelevantReply(replyItem);
      boolean isHidden =
          replyItem.isRepeated() || replyItem.isConflicting() || isIrrelevant || isNotNegative;
      if (!replyItem.isConflicting() && !isIrrelevant && score != null) {
        log.debug("Score added: {}", score);
        reviewScores.add(score);
      }
      if (changeSetData.getReplyFilterEnabled() && isHidden) {
        continue;
      }
      if (changeSetData.getDebugReviewMode()) {
        reply += debugCodeBlocksReview.getDebugCodeBlock(replyItem, isHidden);
      }
      ReviewBatch batchMap = new ReviewBatch(reply);
      if (change.getIsCommentEvent() && replyItem.getId() != null) {
        setCommentBatchMap(batchMap, replyItem.getId());
      } else {
        setPatchSetReviewBatchMap(batchMap, replyItem);
      }
      reviewBatches.add(batchMap);
    }
  }

  private AIChatResponseContent getReviewReply(GerritChange change, String patchSet)
      throws Exception {
    List<String> patchLines = Arrays.asList(patchSet.split("\n"));
    if (patchLines.size() > config.getMaxReviewLines()) {
      log.warn("Patch set too large. Skipping review. changeId: {}", change.getFullChangeId());
      return new AIChatResponseContent(String.format(SPLIT_REVIEW_MSG, config.getMaxReviewLines()));
    }

    return chatAIClient.ask(changeSetData, change, patchSet);
  }

  private Integer getReviewScore(GerritChange change) {
    if (config.isVotingEnabled()) {
      return reviewScores.isEmpty()
          ? (change.getIsCommentEvent() ? null : 0)
          : Collections.min(reviewScores);
    } else {
      return null;
    }
  }

  private boolean isNotNegativeReply(Integer score) {
    return score != null
        && config.getFilterNegativeComments()
        && score >= config.getFilterCommentsBelowScore();
  }

  private boolean isIrrelevantReply(AIChatReplyItem replyItem) {
    return config.getFilterRelevantComments()
        && replyItem.getRelevance() != null
        && replyItem.getRelevance() < config.getFilterCommentsRelevanceThreshold();
  }
}
