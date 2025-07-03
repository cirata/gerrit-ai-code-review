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

package com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.prompt;

import static com.googlesource.gerrit.plugins.aicodereview.utils.TextUtils.DOT;
import static com.googlesource.gerrit.plugins.aicodereview.utils.TextUtils.joinWithSpace;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.stateful.client.prompt.ChatGptPromptStateful;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.prompt.AIChatPrompt;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AIChatGptPromptStatefulBase extends AIChatPrompt
    implements ChatGptPromptStateful {
  public static String DEFAULT_AI_CHAT_ASSISTANT_NAME;
  public static String DEFAULT_AI_CHAT_ASSISTANT_DESCRIPTION;
  public static String DEFAULT_AI_CHAT_ASSISTANT_INSTRUCTIONS;
  public static String DEFAULT_AI_CHAT_MESSAGE_REVIEW;

  protected final ChangeSetData changeSetData;

  private final GerritChange change;

  public AIChatGptPromptStatefulBase(
      Configuration config, ChangeSetData changeSetData, GerritChange change) {
    super(config);
    this.changeSetData = changeSetData;
    this.change = change;
    this.isCommentEvent = change.getIsCommentEvent();
    // Avoid repeated loading of prompt constants
    if (DEFAULT_AI_CHAT_ASSISTANT_NAME == null) {
      loadDefaultPrompts("promptsStateful");
    }
  }

  public String getDefaultGptAssistantDescription() {
    return String.format(DEFAULT_AI_CHAT_ASSISTANT_DESCRIPTION, change.getProjectName());
  }

  public abstract void addGptAssistantInstructions(List<String> instructions);

  public abstract String getAIRequestDataPrompt();

  public String getDefaultGptAssistantInstructions() {
    List<String> instructions =
        new ArrayList<>(
            List.of(
                DEFAULT_AI_CHAT_SYSTEM_PROMPT + DOT,
                String.format(DEFAULT_AI_CHAT_ASSISTANT_INSTRUCTIONS, change.getProjectName())));
    addGptAssistantInstructions(instructions);

    return joinWithSpace(instructions);
  }

  public String getDefaultGptThreadReviewMessage(String patchSet) {
    String gptRequestDataPrompt = getAIRequestDataPrompt();
    if (gptRequestDataPrompt != null && !gptRequestDataPrompt.isEmpty()) {
      log.debug("Request User Prompt retrieved: {}", gptRequestDataPrompt);
      return gptRequestDataPrompt;
    } else {
      return String.format(DEFAULT_AI_CHAT_MESSAGE_REVIEW, patchSet);
    }
  }
}
