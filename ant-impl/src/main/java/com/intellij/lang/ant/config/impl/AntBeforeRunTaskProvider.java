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
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.AntConfiguration;
import consulo.annotation.component.ExtensionImpl;
import consulo.apache.ant.ApacheAntIcons;
import consulo.apache.ant.impl.localize.ApacheAntImplLocalize;
import consulo.dataContext.DataContext;
import consulo.execution.BeforeRunTaskProvider;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.util.concurrent.AsyncResult;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

/**
 * @author Vladislav.Kaznacheev
 */
@ExtensionImpl
public class AntBeforeRunTaskProvider extends BeforeRunTaskProvider<AntBeforeRunTask> {
  public static final Key<AntBeforeRunTask> ID = Key.create("AntTarget");
  private final Project myProject;

  @Inject
  public AntBeforeRunTaskProvider(Project project) {
    myProject = project;
  }

  @Nonnull
  @Override
  public Key<AntBeforeRunTask> getId() {
    return ID;
  }

  @Nonnull
  @Override
  public LocalizeValue getName() {
    return ApacheAntImplLocalize.antTargetBeforeRunDescriptionEmpty();
  }

  @Override
  public Image getIcon() {
    return ApacheAntIcons.Target;
  }

  @Override
  public Image getTaskIcon(AntBeforeRunTask task) {
    AntBuildTarget antTarget = findTargetToExecute(task);
    return antTarget instanceof MetaTarget ? ApacheAntIcons.MetaTarget : ApacheAntIcons.Target;
  }

  @Nonnull
  @Override
  public LocalizeValue getDescription(AntBeforeRunTask task) {
    final String targetName = task.getTargetName();
    if (targetName == null) {
      return ApacheAntImplLocalize.antTargetBeforeRunDescriptionEmpty();
    }
    return ApacheAntImplLocalize.antTargetBeforeRunDescription(targetName);
  }

  @Override
  public boolean isConfigurable() {
    return true;
  }

  @Override
  @RequiredUIAccess
  @Nonnull
  public AsyncResult<Void> configureTask(RunConfiguration runConfiguration, AntBeforeRunTask task) {
    AntBuildTarget buildTarget = findTargetToExecute(task);
    final TargetChooserDialog dlg = new TargetChooserDialog(myProject, buildTarget);
    AsyncResult<Void> result = dlg.showAsync();
    result.doWhenDone(() -> {
      task.setTargetName(null);
      task.setAntFileUrl(null);
      AntBuildTarget target = dlg.getSelectedTarget();
      if (target != null) {
        final VirtualFile vFile = target.getModel().getBuildFile().getVirtualFile();
        if (vFile != null) {
          task.setAntFileUrl(vFile.getUrl());
          task.setTargetName(target.getName());
        }
      }
    });

    return result;
  }

  @Override
  public AntBeforeRunTask createTask(RunConfiguration runConfiguration) {
    return new AntBeforeRunTask();
  }

  @Override
  public boolean canExecuteTask(RunConfiguration configuration, AntBeforeRunTask task) {
    return findTargetToExecute(task) != null;
  }

  @Override
  public boolean executeTask(DataContext context, RunConfiguration configuration, ExecutionEnvironment env, AntBeforeRunTask task) {
    final AntBuildTarget target = findTargetToExecute(task);
    if (target != null) {
      return AntConfigurationImpl.executeTargetSynchronously(context, target);
    }
    return true;
  }

  @Nullable
  private AntBuildTarget findTargetToExecute(AntBeforeRunTask task) {
    final String fileUrl = task.getAntFileUrl();
    final String targetName = task.getTargetName();
    if (fileUrl == null || targetName == null) {
      return null;
    }
    final VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(fileUrl);
    if (vFile == null) {
      return null;
    }
    final AntConfigurationImpl antConfiguration = (AntConfigurationImpl)AntConfiguration.getInstance(myProject);
    for (AntBuildFile buildFile : antConfiguration.getBuildFiles()) {
      if (vFile.equals(buildFile.getVirtualFile())) {
        final AntBuildTarget target = buildFile.getModel().findTarget(targetName);
        if (target != null) {
          return target;
        }
        for (AntBuildTarget metaTarget : antConfiguration.getMetaTargets(buildFile)) {
          if (targetName.equals(metaTarget.getName())) {
            return metaTarget;
          }
        }
        return null;
      }
    }
    return null;
  }
}
