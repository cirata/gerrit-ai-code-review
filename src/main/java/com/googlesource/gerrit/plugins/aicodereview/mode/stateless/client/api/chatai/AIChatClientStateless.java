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

package com.googlesource.gerrit.plugins.aicodereview.mode.stateless.client.api.chatai;

import static com.googlesource.gerrit.plugins.aicodereview.utils.GsonUtils.getNoEscapedGson;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.HttpHeaders;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.common.client.api.openapi.ChatAIClient;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.openai.AIChatClient;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.openai.AIChatParameters;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.openai.AIChatTools;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.http.HttpClientWithRetry;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatCompletionRequest;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatRequestMessage;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatResponseContent;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatTool;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateless.client.api.UriResourceLocatorStateless;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateless.client.prompt.AIChatPromptStateless;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.NameValuePair;
import org.apache.http.entity.ContentType;

@Slf4j
@Singleton
public class AIChatClientStateless extends AIChatClient implements ChatAIClient {
  private static final int REVIEW_ATTEMPT_LIMIT = 3;

  private final HttpClientWithRetry httpClientWithRetry = new HttpClientWithRetry();

  @VisibleForTesting
  @Inject
  public AIChatClientStateless(Configuration config) {
    super(config);
    httpClientWithRetry.setEnableMessageDebugging(config.getEnableMessageDebugging());
  }

  public AIChatResponseContent ask(
      ChangeSetData changeSetData, GerritChange change, String patchSet) throws Exception {
    isCommentEvent = change.getIsCommentEvent();
    String changeId = change.getFullChangeId();
    log.info(
        "Processing STATELESS AIChat Request with changeId: {}, Patch Set: {}", changeId, patchSet);
    for (int attemptInd = 0; attemptInd < REVIEW_ATTEMPT_LIMIT; attemptInd++) {
      HttpRequest request = createRequest(config, changeSetData, patchSet);
      log.debug("AIChat request: {}", request.toString());

      HttpResponse<String> response = httpClientWithRetry.execute(request);

      String body = response.body();
      log.debug("Chat response body: {}", body);
      if (body == null) {
        throw new IOException("AIChat response body is null");
      }

      AIChatResponseContent contentExtracted = extractContent(config, body);
      if (validateResponse(contentExtracted, changeId, attemptInd)) {
        return contentExtracted;
      }
    }
    throw new RuntimeException("Failed to receive valid AIChat response");
  }

  protected HttpRequest createRequest(
      Configuration config, ChangeSetData changeSetData, String patchSet) {
    URI uri =
        URI.create(config.getAIDomain() + UriResourceLocatorStateless.getChatResourceUri(config));
    log.debug("AIChat request URI: {}", uri);
    requestBody = createRequestBody(config, changeSetData, patchSet);
    log.debug("AIChat request body: {}", requestBody);

    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
            .uri(uri)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody));

    // depending on the aiType, add appropriate authorization header ( if required ).
    NameValuePair authHeader = config.getAuthorizationHeaderInfo();
    if (authHeader != null) {
      builder.header(authHeader.getName(), authHeader.getValue());
    }
    return builder.build();
  }

  private String createRequestBody(
      Configuration config, ChangeSetData changeSetData, String patchSet) {
    AIChatPromptStateless AIChatPromptStateless = new AIChatPromptStateless(config, isCommentEvent);
    AIChatRequestMessage systemMessage =
        AIChatRequestMessage.builder()
            .role("system")
            .content(AIChatPromptStateless.getAISystemPrompt())
            .build();
    AIChatRequestMessage userMessage =
        AIChatRequestMessage.builder()
            .role("user")
            .content(AIChatPromptStateless.getGptUserPrompt(changeSetData, patchSet))
            .build();

    AIChatParameters AIChatParameters = new AIChatParameters(config, isCommentEvent);
    AIChatTool[] tools = new AIChatTool[] {AIChatTools.retrieveFormatRepliesTool()};
    AIChatCompletionRequest chatGptCompletionRequest =
        AIChatCompletionRequest.builder()
            .model(config.getAIModel())
            .messages(List.of(systemMessage, userMessage))
            .temperature(AIChatParameters.getGptTemperature())
            .stream(AIChatParameters.getStreamOutput())
            // Seed value is Utilized to prevent ChatGPT from mixing up separate API calls that
            // occur in close
            // temporal proximity.
            .seed(AIChatParameters.getRandomSeed())
            .tools(tools)
            .toolChoice(AIChatTools.retrieveFormatRepliesToolChoice())
            .build();

    return getNoEscapedGson().toJson(chatGptCompletionRequest);
  }
}
