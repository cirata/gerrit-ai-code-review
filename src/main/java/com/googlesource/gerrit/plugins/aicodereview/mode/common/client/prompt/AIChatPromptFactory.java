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

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.common.client.prompt.ChatAIDataPrompt;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.stateful.client.prompt.ChatGptPromptStateful;
import com.googlesource.gerrit.plugins.aicodereview.localization.Localizer;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.GerritClientData;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.prompt.AIChatDataPromptRequestsStateful;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.prompt.AIChatGptPromptStatefulRequests;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.prompt.AIChatGptPromptStatefulReview;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateless.client.prompt.AIChatDataPromptRequestsStateless;
import com.googlesource.gerrit.plugins.aicodereview.settings.Settings;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AIChatPromptFactory {

  public static ChatGptPromptStateful getAIChatPromptStateful(
      Configuration config, ChangeSetData changeSetData, GerritChange change) {
    if (change.getIsCommentEvent()) {
      log.info("AIChatPromptFactory: Returned AIChatGptPromptStatefulRequests");
      return new AIChatGptPromptStatefulRequests(config, changeSetData, change);
    } else {
      log.info("AIChatPromptFactory: Returned AIChatGptPromptStatefulReview");
      return new AIChatGptPromptStatefulReview(config, changeSetData, change);
    }
  }

  public static ChatAIDataPrompt getChatDataPrompt(
      Configuration config,
      ChangeSetData changeSetData,
      GerritChange change,
      GerritClientData gerritClientData,
      Localizer localizer) {
    if (change.getIsCommentEvent()) {
      if ((config.getAIMode() == Settings.Modes.stateless)) {
        log.info("AIChatPromptFactory: Returned AIChatDataPromptRequestsStateless");
        return new AIChatDataPromptRequestsStateless(
            config, changeSetData, gerritClientData, localizer);
      } else {
        log.info("AIChatPromptFactory: Returned AIChatDataPromptRequestsStateful");
        return new AIChatDataPromptRequestsStateful(
            config, changeSetData, gerritClientData, localizer);
      }
    } else {
      log.info("AIChatPromptFactory: Returned AIChatDataPromptReview");
      return new AIChatDataPromptReview(config, changeSetData, gerritClientData, localizer);
    }
  }
}
