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

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.messages;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.data.PluginDataHandlerProvider;
import com.googlesource.gerrit.plugins.aicodereview.localization.Localizer;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.ClientBase;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.commands.ClientCommands;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientMessage extends ClientBase {
  private static final Pattern MESSAGE_HEADING_PATTERN =
      Pattern.compile("^(?:Patch Set \\d+:[^\\n]*\\s+(?:\\(\\d+ comments?\\)\\s*)?)+");

  private final Pattern botMentionPattern;
  private final ClientCommands clientCommands;
  private final DebugCodeBlocksReview debugCodeBlocksReview;
  private final DebugCodeBlocksDynamicSettings debugCodeBlocksDynamicSettings;

  @Getter private String message;

  public ClientMessage(
      Configuration config,
      ChangeSetData changeSetData,
      PluginDataHandlerProvider pluginDataHandlerProvider,
      Localizer localizer) {
    super(config);
    botMentionPattern = getBotMentionPattern();
    clientCommands =
        new ClientCommands(config, changeSetData, pluginDataHandlerProvider, localizer);
    debugCodeBlocksReview = new DebugCodeBlocksReview(localizer);
    debugCodeBlocksDynamicSettings = new DebugCodeBlocksDynamicSettings(localizer);
  }

  public ClientMessage(
      Configuration config, ChangeSetData changeSetData, String message, Localizer localizer) {
    this(config, changeSetData, (PluginDataHandlerProvider) null, localizer);
    this.message = message;
  }

  public boolean isBotAddressed(String message) {
    log.debug("Processing comment: {}", message);
    Matcher userMatcher = botMentionPattern.matcher(message);
    if (!userMatcher.find()) {
      log.debug(
          "Skipping action since the comment does not mention the AIChat bot."
              + " Expected bot name in comment: {}, Actual comment text: {}",
          config.getGerritUserName(),
          message);
      return false;
    }
    return true;
  }

  public ClientMessage removeHeadings() {
    message = MESSAGE_HEADING_PATTERN.matcher(message).replaceAll("");
    return this;
  }

  public ClientMessage removeMentions() {
    message = botMentionPattern.matcher(message).replaceAll("").trim();
    return this;
  }

  public ClientMessage parseRemoveCommands() {
    message = clientCommands.parseRemoveCommands(message);
    return this;
  }

  public ClientMessage removeDebugCodeBlocksReview() {
    message = debugCodeBlocksReview.removeDebugCodeBlocks(message);
    return this;
  }

  public ClientMessage removeDebugCodeBlocksDynamicSettings() {
    message = debugCodeBlocksDynamicSettings.removeDebugCodeBlocks(message);
    return this;
  }

  public boolean isContainingHistoryCommand() {
    return clientCommands.isContainingHistoryCommand();
  }

  public boolean parseCommands(String comment, boolean isNotHistory) {
    return clientCommands.parseCommands(comment, isNotHistory);
  }

  public void processHistoryCommand() {
    clientCommands.getDirectives().copyDirectiveToSettings();
  }

  private Pattern getBotMentionPattern() {
    String emailRegex = "^(?!>).*?(?:@" + getUserNameOrEmail() + ")\\b";
    return Pattern.compile(emailRegex, Pattern.MULTILINE);
  }

  private String getUserNameOrEmail() {
    String escapedUserName = Pattern.quote(config.getGerritUserName());
    String userEmail = config.getGerritUserEmail();
    if (userEmail.isBlank()) {
      return escapedUserName;
    }
    return escapedUserName + "|" + Pattern.quote(userEmail);
  }
}
