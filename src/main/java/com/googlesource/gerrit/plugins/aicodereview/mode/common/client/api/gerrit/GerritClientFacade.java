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

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.common.client.api.gerrit.GerritClientPatchSetInfo;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.patch.diff.FileDiffProcessed;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.gerrit.GerritPermittedVotingRange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.GerritClientData;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GerritClientFacade {
  private final ChangeSetData changeSetData;
  private final GerritClientDetail gerritClientDetail;
  private final GerritClientComments gerritClientComments;
  private final GerritClientPatchSetInfo gerritClientPatchSetInfo;

  @VisibleForTesting
  @Inject
  public GerritClientFacade(
      Configuration config,
      ChangeSetData changeSetData,
      GerritClientComments gerritClientComments,
      GerritClientPatchSetInfo gerritClientPatchSetInfo) {
    gerritClientDetail = new GerritClientDetail(config, changeSetData);
    this.gerritClientPatchSetInfo = gerritClientPatchSetInfo;
    this.changeSetData = changeSetData;
    this.gerritClientComments = gerritClientComments;
  }

  public GerritPermittedVotingRange getPermittedVotingRange(GerritChange change) {
    return gerritClientDetail.getPermittedVotingRange(change);
  }

  public String getPatchSet(GerritChange change) throws Exception {
    return gerritClientPatchSetInfo.getPatchSet(changeSetData, change);
  }

  public boolean isDisabledUser(String authorUsername) {
    return gerritClientPatchSetInfo.isDisabledUser(authorUsername);
  }

  public boolean isDisabledTopic(String topic) {
    return gerritClientPatchSetInfo.isDisabledTopic(topic);
  }

  public boolean isWorkInProgress(GerritChange change) {
    return gerritClientDetail.isWorkInProgress(change);
  }

  public HashMap<String, FileDiffProcessed> getFileDiffsProcessed() {
    return gerritClientPatchSetInfo.getFileDiffsProcessed();
  }

  public Integer getNotNullAccountId(String authorUsername) {
    return gerritClientPatchSetInfo.getNotNullAccountId(authorUsername);
  }

  public boolean retrieveLastComments(GerritChange change) {
    return gerritClientComments.retrieveLastComments(change);
  }

  public void retrievePatchSetInfo(GerritChange change) {
    gerritClientComments.retrieveAllComments(change);
    gerritClientPatchSetInfo.retrieveRevisionBase(change);
  }

  public GerritClientData getClientData(GerritChange change) {
    return new GerritClientData(
        gerritClientPatchSetInfo.getFileDiffsProcessed(),
        gerritClientDetail.getMessages(change),
        gerritClientComments.getCommentData(),
        gerritClientPatchSetInfo.getRevisionBase());
  }
}
