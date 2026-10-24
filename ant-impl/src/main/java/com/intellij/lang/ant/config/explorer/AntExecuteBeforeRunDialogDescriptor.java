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
package com.intellij.lang.ant.config.explorer;

import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.impl.AntBeforeRunTask;
import consulo.apache.ant.impl.localize.ApacheAntImplLocalize;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.execution.BeforeRunTask;
import consulo.execution.RunManager;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.ConfigurationType;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.localize.ExecutionLocalize;
import consulo.project.Project;
import consulo.ui.CheckBoxTree;
import consulo.ui.CheckBoxTreePolicy;
import consulo.ui.Component;
import consulo.ui.TextItemPresentation;
import consulo.ui.TreeModel;
import consulo.ui.TreeNode;
import consulo.ui.UIAccess;
import consulo.ui.WidthAndHeight;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.dialog.DialogDescriptor;
import consulo.ui.layout.ScrollableLayout;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Assigns an Ant target as a before-run task to run configurations and configuration type templates.
 */
public final class AntExecuteBeforeRunDialogDescriptor extends DialogDescriptor {
  private final Project myProject;
  private final AntBuildTarget myTarget;
  private final Map<ConfigurationType, List<RunConfiguration>> myConfigurations = new LinkedHashMap<>();
  private final Set<Object> myAssigned = new HashSet<>();

  private @Nullable CheckBoxTree<Object> myTree;

  public AntExecuteBeforeRunDialogDescriptor(Project project, AntBuildTarget target) {
    super(ExecutionLocalize.executeBeforeRunDebugDialogTitle(ApacheAntImplLocalize.antTarget()));
    myProject = project;
    myTarget = target;

    RunManager runManager = RunManager.getInstance(project);
    for (ConfigurationType type : runManager.getConfigurationFactories()) {
      if (isAssigned(type)) {
        myAssigned.add(type);
      }

      List<RunConfiguration> configurations = new ArrayList<>();
      Set<String> addedNames = new HashSet<>();
      for (RunConfiguration configuration : runManager.getConfigurations(type)) {
        if (addedNames.add(configuration.getName())) {
          configurations.add(configuration);
          if (isAssigned(configuration)) {
            myAssigned.add(configuration);
          }
        }
      }
      myConfigurations.put(type, configurations);
    }
  }

  @Override
  public @Nullable WidthAndHeight getInitialSize() {
    return WidthAndHeight.ofPixel(400, 400);
  }

  @RequiredUIAccess
  @Override
  public Component createCenterComponent(Disposable uiDisposable) {
    CheckBoxTree<Object> tree = CheckBoxTree.create(new ConfigurationTreeModel());
    tree.setCheckPolicy(CheckBoxTreePolicy.INDEPENDENT);
    Disposer.register(uiDisposable, tree.destroyHook());
    for (Object value : myAssigned) {
      tree.setChecked(value, true);
    }
    myTree = tree;

    expandAssigned(tree);
    return ScrollableLayout.create(tree);
  }

  @RequiredUIAccess
  private void expandAssigned(CheckBoxTree<Object> tree) {
    TreeNode<Object> rootNode = tree.getRootNode();
    if (rootNode == null) {
      return;
    }

    UIAccess uiAccess = UIAccess.current();
    myConfigurations.forEach((type, configurations) -> {
      boolean hasAssigned = false;
      for (RunConfiguration configuration : configurations) {
        if (myAssigned.contains(configuration)) {
          hasAssigned = true;
          break;
        }
      }

      if (hasAssigned) {
        rootNode.findChild(value -> value == type).whenCompleteAsync((node, error) -> {
          if (node != null) {
            tree.expand(node, 1);
          }
        }, uiAccess);
      }
    });
  }

  @RequiredUIAccess
  public void apply() {
    CheckBoxTree<Object> tree = myTree;
    if (tree == null) {
      return;
    }

    RunManager runManager = RunManager.getInstance(myProject);
    myConfigurations.forEach((type, configurations) -> {
      for (RunConfiguration configuration : configurations) {
        update(runManager, configuration, tree.isChecked(configuration));
      }

      boolean typeChecked = tree.isChecked(type);
      for (ConfigurationFactory factory : type.getConfigurationFactories()) {
        update(runManager, runManager.getConfigurationTemplate(factory).getConfiguration(), typeChecked);
      }
    });
  }

  private void update(RunManager runManager, RunConfiguration configuration, boolean enabled) {
    List<BeforeRunTask> tasks = new ArrayList<>(runManager.getBeforeRunTasks(configuration));
    boolean present = false;
    for (BeforeRunTask task : tasks) {
      if (task instanceof AntBeforeRunTask antTask && antTask.isRunningTarget(myTarget)) {
        present = true;
        break;
      }
    }

    if (enabled == present) {
      return;
    }

    if (enabled) {
      AntBeforeRunTask task = new AntBeforeRunTask();
      task.setTarget(myTarget);
      task.setEnabled(true);
      tasks.add(task);
    }
    else {
      tasks.removeIf(task -> task instanceof AntBeforeRunTask antTask && antTask.isRunningTarget(myTarget));
    }
    runManager.setBeforeRunTasks(configuration, tasks, false);
  }

  private boolean isAssigned(ConfigurationType type) {
    RunManager runManager = RunManager.getInstance(myProject);
    for (ConfigurationFactory factory : type.getConfigurationFactories()) {
      if (isAssigned(runManager.getConfigurationTemplate(factory).getConfiguration())) {
        return true;
      }
    }
    return false;
  }

  private boolean isAssigned(RunConfiguration configuration) {
    for (AntBeforeRunTask task : RunManager.getInstance(myProject).getBeforeRunTasks(configuration, AntBeforeRunTask.class)) {
      if (task.isRunningTarget(myTarget)) {
        return true;
      }
    }
    return false;
  }

  private static void render(Object value, TextItemPresentation presentation) {
    if (value instanceof ConfigurationType type) {
      presentation.withIcon(type.getIcon());
      presentation.append(type.getDisplayName());
    }
    else if (value instanceof RunConfiguration configuration) {
      presentation.append(configuration.getName());
    }
  }

  private class ConfigurationTreeModel implements TreeModel<Object> {
    @Override
    public void buildChildren(Function<Object, TreeNode<Object>> nodeFactory, @Nullable Object parentValue) {
      if (parentValue == null) {
        myConfigurations.forEach((type, configurations) -> {
          TreeNode<Object> node = nodeFactory.apply(type);
          node.setLeaf(configurations.isEmpty());
          node.setRenderer(AntExecuteBeforeRunDialogDescriptor::render);
        });
      }
      else if (parentValue instanceof ConfigurationType type) {
        for (RunConfiguration configuration : myConfigurations.getOrDefault(type, List.of())) {
          TreeNode<Object> node = nodeFactory.apply(configuration);
          node.setLeaf(true);
          node.setRenderer(AntExecuteBeforeRunDialogDescriptor::render);
        }
      }
    }
  }
}
