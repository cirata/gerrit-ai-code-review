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

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.openai;

import static com.googlesource.gerrit.plugins.aicodereview.utils.GsonUtils.getGson;

import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatTool;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatToolChoice;
import com.googlesource.gerrit.plugins.aicodereview.utils.FileUtils;
import java.io.IOException;
import java.io.InputStreamReader;

public class AIChatTools {
  public static AIChatTool retrieveFormatRepliesTool() {
    AIChatTool tools;
    try (InputStreamReader reader =
        FileUtils.getInputStreamReader("config/formatRepliesTool.json")) {
      tools = getGson().fromJson(reader, AIChatTool.class);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load data for AIChat `format_replies` tool", e);
    }
    return tools;
  }

  public static AIChatToolChoice retrieveFormatRepliesToolChoice() {
    AIChatToolChoice toolChoice;
    try (InputStreamReader reader =
        FileUtils.getInputStreamReader("config/formatRepliesToolChoice.json")) {
      toolChoice = getGson().fromJson(reader, AIChatToolChoice.class);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load data for AIChat `format_replies` tool choice", e);
    }
    return toolChoice;
  }
}
