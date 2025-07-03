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

package com.googlesource.gerrit.plugins.aicodereview.mode.stateless.client.prompt;

import static com.googlesource.gerrit.plugins.aicodereview.utils.TextUtils.*;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.prompt.AIChatPrompt;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AIChatPromptStateless extends AIChatPrompt {
  public static String DEFAULT_AI_CHAT_SYSTEM_PROMPT_INPUT_DESCRIPTION;
  public static String DEFAULT_AI_CHAT_SYSTEM_PROMPT_INPUT_DESCRIPTION_REVIEW;
  public static String DEFAULT_AI_CHAT_REVIEW_PROMPT;
  public static String DEFAULT_AI_CHAT_REVIEW_PROMPT_REVIEW;
  public static String DEFAULT_AI_CHAT_REVIEW_PROMPT_MESSAGE_HISTORY;
  public static String DEFAULT_AI_CHAT_REVIEW_PROMPT_DIFF;

  public AIChatPromptStateless(Configuration config) {
    super(config);
    loadStatelessPrompts();
  }

  public AIChatPromptStateless(Configuration config, boolean isCommentEvent) {
    super(config, isCommentEvent);
    loadStatelessPrompts();
  }

  public static String getDefaultGptReviewSystemPrompt() {
    return joinWithSpace(
        new ArrayList<>(
            List.of(
                DEFAULT_AI_CHAT_SYSTEM_PROMPT + DOT,
                DEFAULT_AI_CHAT_SYSTEM_PROMPT_INPUT_DESCRIPTION,
                DEFAULT_AI_CHAT_SYSTEM_PROMPT_INPUT_DESCRIPTION_REVIEW)));
  }

  public String getAISystemPrompt() {
    List<String> prompt =
        new ArrayList<>(
            Arrays.asList(
                config.getString(Configuration.KEY_AI_SYSTEM_PROMPT, DEFAULT_AI_CHAT_SYSTEM_PROMPT)
                    + DOT,
                AIChatPromptStateless.DEFAULT_AI_CHAT_SYSTEM_PROMPT_INPUT_DESCRIPTION));
    if (!isCommentEvent) {
      prompt.add(AIChatPromptStateless.DEFAULT_AI_CHAT_SYSTEM_PROMPT_INPUT_DESCRIPTION_REVIEW);
    }
    return joinWithSpace(prompt);
  }

  public String getGptUserPrompt(ChangeSetData changeSetData, String patchSet) {
    List<String> prompt = new ArrayList<>();
    String gptRequestDataPrompt = changeSetData.getReviewAIDataPrompt();
    boolean isValidRequestDataPrompt =
        gptRequestDataPrompt != null && !gptRequestDataPrompt.isEmpty();
    if (isCommentEvent && isValidRequestDataPrompt) {
      log.debug("Request User Prompt retrieved: {}", gptRequestDataPrompt);
      prompt.addAll(
          Arrays.asList(
              DEFAULT_AI_CHAT_REQUEST_PROMPT_DIFF,
              patchSet,
              DEFAULT_AI_CHAT_REQUEST_PROMPT_REQUESTS,
              gptRequestDataPrompt,
              getCommentRequestPrompt(changeSetData.getCommentPropertiesSize())));
    } else {
      prompt.add(AIChatPromptStateless.DEFAULT_AI_CHAT_REVIEW_PROMPT);
      prompt.addAll(getReviewSteps());
      prompt.add(AIChatPromptStateless.DEFAULT_AI_CHAT_REVIEW_PROMPT_DIFF);
      prompt.add(patchSet);
      if (isValidRequestDataPrompt) {
        prompt.add(AIChatPromptStateless.DEFAULT_AI_CHAT_REVIEW_PROMPT_MESSAGE_HISTORY);
        prompt.add(gptRequestDataPrompt);
      }
      if (!changeSetData.getDirectives().isEmpty()) {
        prompt.add(DEFAULT_AI_CHAT_REVIEW_PROMPT_DIRECTIVES);
        prompt.add(
            getNumberedListString(new ArrayList<>(changeSetData.getDirectives()), null, null));
      }
    }
    return joinWithNewLine(prompt);
  }

  private void loadStatelessPrompts() {
    // Avoid repeated loading of prompt constants
    if (DEFAULT_AI_CHAT_SYSTEM_PROMPT_INPUT_DESCRIPTION == null) {
      loadDefaultPrompts("promptsStateless");
    }
  }

  private List<String> getReviewSteps() {
    List<String> steps =
        new ArrayList<>(
            List.of(
                joinWithSpace(
                    new ArrayList<>(
                        List.of(
                            DEFAULT_AI_CHAT_REVIEW_PROMPT_REVIEW,
                            DEFAULT_AI_CHAT_PROMPT_FORCE_JSON_FORMAT,
                            getPatchSetReviewPrompt())))));
    if (config.getAIReviewCommitMessages()) {
      steps.add(getReviewPromptCommitMessages());
    }
    return steps;
  }
}
