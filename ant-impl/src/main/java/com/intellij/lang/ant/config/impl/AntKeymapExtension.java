/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntConfiguration;
import consulo.annotation.component.ExtensionImpl;
import consulo.apache.ant.ApacheAntIcons;
import consulo.application.ApplicationManager;
import consulo.component.ComponentManager;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.keymap.KeyMapBundle;
import consulo.ui.ex.keymap.KeymapExtension;
import consulo.ui.ex.keymap.KeymapGroup;
import consulo.ui.ex.keymap.KeymapGroupFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * @author Vladislav.Kaznacheev
 */
@ExtensionImpl
public class AntKeymapExtension implements KeymapExtension {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.config.impl.AntProjectKeymap");

  public KeymapGroup createGroup(final Predicate<AnAction> filtered, ComponentManager project) {
    final Map<AntBuildFile, KeymapGroup> buildFileToGroup = new HashMap<AntBuildFile, KeymapGroup>();
    final KeymapGroup result =
      KeymapGroupFactory.getInstance().createGroup(KeyMapBundle.message("ant.targets.group.title"), ApacheAntIcons.AntGroup);

    final ActionManager actionManager = ActionManager.getInstance();
    final String[] ids =
      actionManager.getActionIds(project != null ? AntConfiguration.getActionIdPrefix((Project)project) : AntConfiguration.ACTION_ID_PREFIX);
    Arrays.sort(ids);

    if (project != null) {
      final AntConfiguration antConfiguration = AntConfiguration.getInstance((Project)project);
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          for (final String id : ids) {
            if (filtered != null && !filtered.test(actionManager.getActionOrStub(id))) {
              continue;
            }
            final AntBuildFile buildFile = antConfiguration.findBuildFileByActionId(id);
            if (buildFile != null) {
              KeymapGroup subGroup = buildFileToGroup.get(buildFile);
              if (subGroup == null) {
                subGroup = KeymapGroupFactory.getInstance().createGroup(buildFile.getPresentableName());
                buildFileToGroup.put(buildFile, subGroup);
                result.addGroup(subGroup);
              }
              subGroup.addActionId(id);
            }
            else {
              LOG.info("no buildfile found for actionId=" + id);
            }
          }
        }
      });
    }

    return result;
  }
}
