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

package com.googlesource.gerrit.plugins.aicodereview.listener;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.data.PluginDataHandlerProvider;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.listener.IEventHandlerType;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.chatgpt.ChatGptAssistant;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.git.GitRepoFiles;
import com.googlesource.gerrit.plugins.aicodereview.settings.Settings;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventHandlerTypeChangeMerged implements IEventHandlerType {
  private final Configuration config;
  private final ChangeSetData changeSetData;
  private final GerritChange change;
  private final GitRepoFiles gitRepoFiles;
  private final PluginDataHandlerProvider pluginDataHandlerProvider;

  EventHandlerTypeChangeMerged(
      Configuration config,
      ChangeSetData changeSetData,
      GerritChange change,
      GitRepoFiles gitRepoFiles,
      PluginDataHandlerProvider pluginDataHandlerProvider) {
    this.config = config;
    this.changeSetData = changeSetData;
    this.change = change;
    this.gitRepoFiles = gitRepoFiles;
    this.pluginDataHandlerProvider = pluginDataHandlerProvider;
  }

  @Override
  public PreprocessResult preprocessEvent() {
    return PreprocessResult.OK;
  }

  @Override
  public void processEvent() {
    // No vector store is setup or used in OLLAMA currently, but also if the review mode is
    // stateless
    // then the vectorstore and ChatGPT assistant will never be used.
    // For now simply protect against only stateful requests performing this update.
    if (config.getAIMode() == Settings.Modes.stateless) {
      log.info(
          "Ignoring ChangeMergedEvent for stateless configurations, as no vector store is required.");
      return;
    }

    ChatGptAssistant chatGptAssistant =
        new ChatGptAssistant(
            config, changeSetData, change, gitRepoFiles, pluginDataHandlerProvider);
    chatGptAssistant.flushAssistantIds();
    chatGptAssistant.createVectorStore();
  }
}
