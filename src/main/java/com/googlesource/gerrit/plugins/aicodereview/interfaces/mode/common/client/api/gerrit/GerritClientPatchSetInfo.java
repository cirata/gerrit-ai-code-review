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

package com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.common.client.api.gerrit;

import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.patch.diff.FileDiffProcessed;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import java.util.HashMap;

public interface GerritClientPatchSetInfo {
  String getPatchSet(ChangeSetData changeSetData, GerritChange gerritChange) throws Exception;

  boolean isDisabledUser(String authorUsername);

  boolean isDisabledTopic(String topic);

  void retrieveRevisionBase(GerritChange change);

  Integer getNotNullAccountId(String authorUsername);

  HashMap<String, FileDiffProcessed> getFileDiffsProcessed();

  Integer getRevisionBase();
}
