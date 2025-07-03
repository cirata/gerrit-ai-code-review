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

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.prompt;

import static com.googlesource.gerrit.plugins.aicodereview.utils.GsonUtils.getGson;
import static com.googlesource.gerrit.plugins.aicodereview.utils.TextUtils.INLINE_CODE_DELIMITER;
import static com.googlesource.gerrit.plugins.aicodereview.utils.TextUtils.SPACE;
import static com.googlesource.gerrit.plugins.aicodereview.utils.TextUtils.joinWithComma;
import static com.googlesource.gerrit.plugins.aicodereview.utils.TextUtils.joinWithSemicolon;
import static com.googlesource.gerrit.plugins.aicodereview.utils.TextUtils.joinWithSpace;

import com.google.gson.reflect.TypeToken;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.utils.FileUtils;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AIChatPrompt {
  // Reply attributes
  public static final String ATTRIBUTE_ID = "id";
  public static final String ATTRIBUTE_REPLY = "reply";
  public static final String ATTRIBUTE_SCORE = "score";
  public static final String ATTRIBUTE_REPEATED = "repeated";
  public static final String ATTRIBUTE_CONFLICTING = "conflicting";
  public static final String ATTRIBUTE_RELEVANCE = "relevance";
  public static final String ATTRIBUTE_CHANGE_ID = "changeId";
  public static final List<String> PATCH_SET_REVIEW_REPLY_ATTRIBUTES =
      new ArrayList<>(
          Arrays.asList(
              ATTRIBUTE_REPLY,
              ATTRIBUTE_SCORE,
              ATTRIBUTE_REPEATED,
              ATTRIBUTE_CONFLICTING,
              ATTRIBUTE_RELEVANCE));
  public static final List<String> REQUEST_REPLY_ATTRIBUTES =
      new ArrayList<>(Arrays.asList(ATTRIBUTE_REPLY, ATTRIBUTE_ID, ATTRIBUTE_CHANGE_ID));

  // Prompt constants loaded from JSON file
  public static String DEFAULT_AI_CHAT_SYSTEM_PROMPT;
  public static String DEFAULT_AI_CHAT_REVIEW_PROMPT_DIRECTIVES;
  public static String DEFAULT_AI_CHAT_PROMPT_FORCE_JSON_FORMAT;
  public static String DEFAULT_AI_CHAT_REPLIES_PROMPT_SPECS;
  public static String DEFAULT_AI_CHAT_REPLIES_PROMPT_INLINE;
  public static String DEFAULT_AI_CHAT_REPLIES_PROMPT_ENFORCE_RESPONSE_CHECK;
  public static String DEFAULT_AI_CHAT_REQUEST_PROMPT_DIFF;
  public static String DEFAULT_AI_CHAT_REQUEST_PROMPT_REQUESTS;
  public static String DEFAULT_AI_CHAT_REVIEW_PROMPT_COMMIT_MESSAGES;
  public static String DEFAULT_AI_CHAT_RELEVANCE_RULES;
  public static String DEFAULT_AI_CHAT_HOW_TO_FIND_COMMIT_MESSAGE;
  public static Map<String, String> DEFAULT_AI_CHAT_REPLIES_ATTRIBUTES;

  protected final Configuration config;

  @Setter protected boolean isCommentEvent;

  public AIChatPrompt(Configuration config) {
    this(config, false);
  }

  public AIChatPrompt(Configuration config, boolean isCommentEvent) {
    this.config = config;
    this.isCommentEvent = isCommentEvent;
    // Avoid repeated loading of prompt constants
    if (DEFAULT_AI_CHAT_SYSTEM_PROMPT == null) {
      loadDefaultPrompts("prompts");
    }
  }

  public static String getCommentRequestPrompt(int commentPropertiesSize) {
    return joinWithSpace(
        new ArrayList<>(
            List.of(
                DEFAULT_AI_CHAT_PROMPT_FORCE_JSON_FORMAT,
                buildFieldSpecifications(REQUEST_REPLY_ATTRIBUTES),
                DEFAULT_AI_CHAT_REPLIES_PROMPT_INLINE,
                String.format(
                    DEFAULT_AI_CHAT_REPLIES_PROMPT_ENFORCE_RESPONSE_CHECK,
                    commentPropertiesSize))));
  }

  public static String getReviewPromptCommitMessages() {
    return String.format(
        DEFAULT_AI_CHAT_REVIEW_PROMPT_COMMIT_MESSAGES, DEFAULT_AI_CHAT_HOW_TO_FIND_COMMIT_MESSAGE);
  }

  protected void loadDefaultPrompts(String promptFilename) {
    String promptFile = String.format("config/%s.json", promptFilename);
    Class<? extends AIChatPrompt> me = this.getClass();
    try (InputStreamReader reader = FileUtils.getInputStreamReader(promptFile)) {
      Map<String, Object> values =
          getGson().fromJson(reader, new TypeToken<Map<String, Object>>() {}.getType());
      for (Map.Entry<String, Object> entry : values.entrySet()) {
        try {
          Field field = me.getField(entry.getKey());
          field.setAccessible(true);
          field.set(null, entry.getValue());
        } catch (NoSuchFieldException | IllegalAccessException e) {
          log.error("Error setting prompt '{}'", entry.getKey(), e);
          throw new IOException();
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to load prompts", e);
    }
    // Keep the given order of attributes
    DEFAULT_AI_CHAT_REPLIES_ATTRIBUTES = new LinkedHashMap<>(DEFAULT_AI_CHAT_REPLIES_ATTRIBUTES);
  }

  protected static String buildFieldSpecifications(List<String> filterFields) {
    Set<String> orderedFilterFields = new LinkedHashSet<>(filterFields);
    Map<String, String> attributes =
        DEFAULT_AI_CHAT_REPLIES_ATTRIBUTES.entrySet().stream()
            .filter(entry -> orderedFilterFields.contains(entry.getKey()))
            .collect(
                Collectors.toMap(
                    entry -> INLINE_CODE_DELIMITER + entry.getKey() + INLINE_CODE_DELIMITER,
                    Map.Entry::getValue,
                    (oldValue, newValue) -> oldValue,
                    LinkedHashMap::new));
    List<String> fieldDescription =
        attributes.entrySet().stream()
            .map(entry -> entry.getKey() + SPACE + entry.getValue())
            .collect(Collectors.toList());

    return String.format(
        DEFAULT_AI_CHAT_REPLIES_PROMPT_SPECS,
        joinWithComma(attributes.keySet()),
        joinWithSemicolon(fieldDescription));
  }

  public String getPatchSetReviewPrompt() {
    List<String> attributes = new ArrayList<>(PATCH_SET_REVIEW_REPLY_ATTRIBUTES);
    if (config.isVotingEnabled() || config.getFilterNegativeComments()) {
      updateScoreDescription();
    } else {
      attributes.remove(ATTRIBUTE_SCORE);
    }
    updateRelevanceDescription();
    return buildFieldSpecifications(attributes) + SPACE + DEFAULT_AI_CHAT_REPLIES_PROMPT_INLINE;
  }

  private void updateScoreDescription() {
    String scoreDescription = DEFAULT_AI_CHAT_REPLIES_ATTRIBUTES.get(ATTRIBUTE_SCORE);
    if (scoreDescription.contains("%d")) {
      scoreDescription =
          String.format(scoreDescription, config.getVotingMinScore(), config.getVotingMaxScore());
      DEFAULT_AI_CHAT_REPLIES_ATTRIBUTES.put(ATTRIBUTE_SCORE, scoreDescription);
    }
  }

  private void updateRelevanceDescription() {
    String relevanceDescription = DEFAULT_AI_CHAT_REPLIES_ATTRIBUTES.get(ATTRIBUTE_RELEVANCE);
    if (relevanceDescription.contains("%s")) {
      String defaultAIRelevanceRules =
          config.getString(Configuration.KEY_AI_RELEVANCE_RULES, DEFAULT_AI_CHAT_RELEVANCE_RULES);
      relevanceDescription = String.format(relevanceDescription, defaultAIRelevanceRules);
      DEFAULT_AI_CHAT_REPLIES_ATTRIBUTES.put(ATTRIBUTE_RELEVANCE, relevanceDescription);
    }
  }
}
