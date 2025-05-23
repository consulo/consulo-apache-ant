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
package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.impl.MetaTarget;
import consulo.application.dumb.DumbAware;
import consulo.project.Project;
import consulo.ui.ex.action.*;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AntBuildActionGroup extends ActionGroup implements DumbAware {

  public void update(AnActionEvent e) {
    Project project = e.getData(Project.KEY);
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(project != null);
    presentation.setVisible(project != null);
  }

  @Nonnull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) {
      return AnAction.EMPTY_ARRAY;
    }
		Project project = e.getData(Project.KEY);
		if (project == null) {
      return AnAction.EMPTY_ARRAY;
    }

    final List<AnAction> children = new ArrayList<AnAction>();
    final AntConfigurationBase antConfiguration = AntConfigurationBase.getInstance(project);
    for (final AntBuildFile buildFile : antConfiguration.getBuildFiles()) {
      final String name = buildFile.getPresentableName();
      DefaultActionGroup subgroup = new DefaultActionGroup();
      subgroup.getTemplatePresentation().setText(name, false);
      subgroup.setPopup(true);
      fillGroup(buildFile, subgroup, antConfiguration);
      if (subgroup.getChildrenCount() > 0) {
        children.add(subgroup);
      }
    }

    return children.toArray(new AnAction[children.size()]);
  }

  private static void fillGroup(final AntBuildFile buildFile, final DefaultActionGroup group, final AntConfiguration antConfiguration) {
    final AntBuildModelBase model = (AntBuildModelBase)buildFile.getModel();
    if (model.getDefaultTargetName() != null) {
      DefaultActionGroup subgroup = new DefaultActionGroup();
      subgroup.add(getOrCreateAction(buildFile, TargetAction.DEFAULT_TARGET_NAME, new String[]{TargetAction.DEFAULT_TARGET_NAME}, null,
                                     model.getDefaultTargetActionId()));
      group.add(subgroup);
    }

    final Set<String> addedTargetNames = new HashSet<>();
    addGroupOfTargets(buildFile, model.getFilteredTargets(), addedTargetNames, group);
    addGroupOfTargets(buildFile, antConfiguration.getMetaTargets(buildFile), addedTargetNames, group);
  }

  private static void addGroupOfTargets(final AntBuildFile buildFile,
                                        final AntBuildTarget[] targets,
                                        final Set<String> addedTargetNames,
                                        final DefaultActionGroup group) {
    final DefaultActionGroup subgroup = new DefaultActionGroup();
    for (final AntBuildTarget target : targets) {
      final String displayName = target.getName();
      if (addedTargetNames.contains(displayName)) {
        continue;
      }
      addedTargetNames.add(displayName);
      final String[] targetsToRun = (target instanceof MetaTarget) ? ((MetaTarget)target).getTargetNames() : new String[]{displayName};
      subgroup.add(getOrCreateAction(buildFile, displayName, targetsToRun, target.getNotEmptyDescription(),
                                     ((AntBuildTargetBase)target).getActionId()));
    }
    if (subgroup.getChildrenCount() > 0) {
      group.add(subgroup);
    }
  }

  private static AnAction getOrCreateAction(final AntBuildFile buildFile,
                                            final String displayName,
                                            final String[] targets,
                                            final String targetDescription,
                                            final String actionId) {
    AnAction action = null;
    if (actionId != null) {
      action = ActionManager.getInstance().getAction(actionId);
    }
    if (action == null) {
      action = new TargetAction(buildFile, displayName, targets, targetDescription);
    }
    return action;
  }
}
