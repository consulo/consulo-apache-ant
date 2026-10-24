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
import com.intellij.lang.ant.config.AntBuildListener;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.AntConfiguration;
import consulo.annotation.component.ExtensionImpl;
import consulo.apache.ant.ApacheAntIcons;
import consulo.apache.ant.impl.localize.ApacheAntImplLocalize;
import consulo.dataContext.DataContext;
import consulo.disposer.Disposable;
import consulo.execution.BeforeRunFragment;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ListBox;
import consulo.ui.TextAttribute;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/**
 * @author Vladislav.Kaznacheev
 */
@ExtensionImpl
public class AntBeforeRunFragment implements BeforeRunFragment<AntBeforeRunTask> {
    public static final String ID = "AntTarget";

    private final Project myProject;

    @Inject
    public AntBeforeRunFragment(Project project) {
        myProject = project;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public LocalizeValue getName() {
        return ApacheAntImplLocalize.antTargetBeforeRunDescriptionEmpty();
    }

    @Override
    public Image getIcon(RunConfiguration configuration) {
        return ApacheAntIcons.Target;
    }

    @Override
    public Image getTaskIcon(RunConfiguration configuration, AntBeforeRunTask task) {
        AntBuildTarget antTarget = findTargetToExecute(task);
        return antTarget instanceof MetaTarget ? ApacheAntIcons.MetaTarget : ApacheAntIcons.Target;
    }

    @Override
    public LocalizeValue getDescription(AntBeforeRunTask task) {
        String targetName = task.getTargetName();
        if (targetName == null) {
            return ApacheAntImplLocalize.antTargetBeforeRunDescriptionEmpty();
        }
        return ApacheAntImplLocalize.antTargetBeforeRunDescription(targetName);
    }

    @Override
    public Class<AntBeforeRunTask> getStateClass() {
        return AntBeforeRunTask.class;
    }

    @Override
    public AntBeforeRunTask createTask(RunConfiguration configuration) {
        return new AntBeforeRunTask();
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<AntBeforeRunTask> createEditor(RunConfiguration configuration, Disposable parent) {
        List<AntBuildTarget> targets = collectTargets();
        ListBox<AntBuildTarget> list = ListBox.create(targets);
        list.setRenderer((presentation, item) -> {
            AntBuildTarget target = item.getValue();
            if (target != null) {
                presentation.withIcon(target instanceof MetaTarget ? ApacheAntIcons.MetaTarget : ApacheAntIcons.Target);
                presentation.append(target.getName());
                AntBuildFile buildFile = target.getModel().getBuildFile();
                if (buildFile != null) {
                    presentation.append(" (" + buildFile.getPresentableName() + ")", TextAttribute.GRAY);
                }
            }
        });

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addBottom(list);
            }

            @RequiredUIAccess
            @Override
            public void reset(AntBeforeRunTask task) {
                for (int i = 0; i < targets.size(); i++) {
                    if (task.isRunningTarget(targets.get(i))) {
                        list.setValueByIndex(i);
                        return;
                    }
                }
            }

            @RequiredUIAccess
            @Override
            public void apply(AntBeforeRunTask task) {
                task.setTarget(list.getValue());
            }
        };
    }

    @Override
    public boolean canExecuteTask(RunConfiguration configuration, AntBeforeRunTask task) {
        return findTargetToExecute(task) != null;
    }

    @Override
    public CompletableFuture<?> executeAsync(
        UIAccess uiAccess,
        DataContext context,
        RunConfiguration configuration,
        ExecutionEnvironment env,
        AntBeforeRunTask task
    ) {
        AntBuildTarget target = findTargetToExecute(task);
        if (target == null) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> result = new CompletableFuture<>();
        uiAccess.giveAsync(() -> {
            if (myProject.isDisposed()) {
                result.completeExceptionally(new CancellationException());
                return;
            }

            target.run(context, List.of(), new AntBuildListener() {
                @Override
                public void buildFinished(int state, int errorCount) {
                    if (state == AntBuildListener.FINISHED_SUCCESSFULLY && errorCount == 0) {
                        result.complete(null);
                    }
                    else {
                        result.completeExceptionally(new CancellationException());
                    }
                }
            });
        }).exceptionally(error -> {
            result.completeExceptionally(error);
            return null;
        });
        return result;
    }

    private List<AntBuildTarget> collectTargets() {
        List<AntBuildTarget> result = new ArrayList<>();
        AntConfiguration antConfiguration = AntConfiguration.getInstance(myProject);
        for (AntBuildFile buildFile : antConfiguration.getBuildFiles()) {
            addTargets(result, antConfiguration.getMetaTargets(buildFile));
            addTargets(result, antConfiguration.getModel(buildFile).getTargets());
        }
        return result;
    }

    private static void addTargets(List<AntBuildTarget> result, AntBuildTarget[] targets) {
        for (AntBuildTarget target : targets) {
            if (target.getName() != null) {
                result.add(target);
            }
        }
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
        final AntConfigurationImpl antConfiguration = (AntConfigurationImpl) AntConfiguration.getInstance(myProject);
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
