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

package com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.gerrit;

import static com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.gerrit.GerritClientPatchSetHelper.extractFilesFromPatch;
import static com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.gerrit.GerritClientPatchSetHelper.filterPatchWithCommitMessage;
import static com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.gerrit.GerritClientPatchSetHelper.filterPatchWithoutCommitMessage;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.common.client.api.gerrit.GerritClientPatchSetInfo;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritClientPatchSet;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GerritClientPatchSetStateful extends GerritClientPatchSet
    implements GerritClientPatchSetInfo {
  private GerritChange change;

  @VisibleForTesting
  @Inject
  public GerritClientPatchSetStateful(Configuration config, AccountCache accountCache) {
    super(config, accountCache);
  }

  public String getPatchSet(ChangeSetData changeSetData, GerritChange change) throws Exception {
    if (change.getIsCommentEvent()) return "";
    this.change = change;

    String formattedPatch = getPatchFromGerrit();
    List<String> files = extractFilesFromPatch(formattedPatch);
    retrieveFileDiff(change, files, revisionBase);

    return formattedPatch;
  }

  private String getPatchFromGerrit() throws Exception {
    try (ManualRequestContext requestContext = config.openRequestContext()) {
      String formattedPatch =
          config
              .getGerritApi()
              .changes()
              .id(
                  change.getProjectName(),
                  change.getBranchNameKey().shortName(),
                  change.getChangeKey().get())
              .current()
              .patch()
              .asString();
      log.debug("Formatted Patch retrieved: {}", formattedPatch);

      return filterPatch(formattedPatch);
    }
  }

  private String filterPatch(String formattedPatch) {
    if (config.getAIReviewCommitMessages()) {
      return filterPatchWithCommitMessage(formattedPatch);
    } else {
      return filterPatchWithoutCommitMessage(change, formattedPatch);
    }
  }
}
