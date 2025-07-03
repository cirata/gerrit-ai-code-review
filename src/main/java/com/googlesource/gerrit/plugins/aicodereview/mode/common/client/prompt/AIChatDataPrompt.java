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

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.common.client.prompt.ChatAIDataPrompt;
import com.googlesource.gerrit.plugins.aicodereview.localization.Localizer;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatMessageItem;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.GerritClientData;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AIChatDataPrompt {
  private final ChatAIDataPrompt aiChatDataPromptHandler;

  public AIChatDataPrompt(
      Configuration config,
      ChangeSetData changeSetData,
      GerritChange change,
      GerritClientData gerritClientData,
      Localizer localizer) {
    aiChatDataPromptHandler =
        AIChatPromptFactory.getChatDataPrompt(
            config, changeSetData, change, gerritClientData, localizer);
  }

  public String buildPrompt() {
    for (int i = 0; i < aiChatDataPromptHandler.getCommentProperties().size(); i++) {
      aiChatDataPromptHandler.addMessageItem(i);
    }
    List<AIChatMessageItem> messageItems = aiChatDataPromptHandler.getMessageItems();
    return messageItems.isEmpty() ? "" : getGson().toJson(messageItems);
  }
}
