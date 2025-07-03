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

import static com.googlesource.gerrit.plugins.aicodereview.listener.EventHandlerTask.EVENT_CLASS_MAP;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventListener;
import com.google.gerrit.server.events.PatchSetEvent;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.aicodereview.config.ConfigCreator;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GerritListener implements EventListener {
  private final String myInstanceId;
  private final ConfigCreator configCreator;
  private final EventHandlerExecutor evenHandlerExecutor;

  @Inject
  public GerritListener(
      ConfigCreator configCreator,
      EventHandlerExecutor evenHandlerExecutor,
      @GerritInstanceId @Nullable String myInstanceId) {
    this.configCreator = configCreator;
    this.evenHandlerExecutor = evenHandlerExecutor;
    this.myInstanceId = myInstanceId;
  }

  @Override
  public void onEvent(Event event) {

    // great we have replication info, check what configuration we have, do we fire
    // everywhere, or just on the originator instance / site.
    PluginConfig pluginConfig = configCreator.getPluginGlobalConfig();
    // Default review at all sites to false, it is an override only used to override
    // normal MultiSite use case.
    boolean reviewFromAllInstances = pluginConfig.getBoolean("reviewFromAllInstances", false);

    // If we aren't told to review at all sites, which is the default behaviour,
    // check if we have matching instanceId so we know to process at this matching site.
    // This is the default behaviour if there is no instanceId present also as it is
    // a single site installation.
    if (!reviewFromAllInstances) {
      if (!Objects.equals(event.instanceId, myInstanceId)) {
        log.debug(
            "Ignore event from another instance myInstanceId {}, event instanceId {}",
            myInstanceId,
            event.instanceId);
        return;
      }
    }

    if (!EVENT_CLASS_MAP.containsValue(event.getClass())) {
      log.debug("The event {} is not managed by the plugin", event);
      return;
    }

    log.info("Processing event: {}", event);
    PatchSetEvent patchSetEvent = (PatchSetEvent) event;
    Project.NameKey projectNameKey = patchSetEvent.getProjectNameKey();
    Change.Key changeKey = patchSetEvent.getChangeKey();

    try {
      Configuration config = configCreator.createConfig(projectNameKey, changeKey);
      evenHandlerExecutor.execute(config, patchSetEvent);
    } catch (NoSuchProjectException e) {
      log.error("Project not found: {}", projectNameKey, e);
    }
  }
}
