/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package consulo.apache.ant.config.actions;

import com.intellij.lang.ant.AntBundle;
import consulo.apache.ant.config.AntBuildFileGroupManager;
import consulo.apache.ant.config.explorer.AntBuildGroupNodeDescriptor;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.tree.AbstractTreeBuilder;
import consulo.ui.ex.awt.tree.Tree;
import consulo.ui.ex.awt.tree.TreeUtil;

import java.util.List;

/**
 * @author VISTALL
 * @since 14:24/09.03.13
 */
public class RemoveGroupsAction extends AnAction {
  private final Tree myTree;

  public RemoveGroupsAction(Tree tree) {
    super(AntBundle.message("remove.groups.name"), AntBundle.message("remove.groups.name"), PlatformIconGroup.generalRemove());
    myTree = tree;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final List<AntBuildGroupNodeDescriptor> groupNodeDescriptors =
      TreeUtil.collectSelectedObjectsOfType(myTree, AntBuildGroupNodeDescriptor.class);

    if (groupNodeDescriptors.isEmpty()) {
      return;
    }

    final AntBuildFileGroupManager groupManager = AntBuildFileGroupManager.getInstance(e.getData(Project.KEY));
    for (AntBuildGroupNodeDescriptor descriptor : groupNodeDescriptors) {
      groupManager.removeGroup(descriptor.getElement());
    }

    final AbstractTreeBuilder builder = AbstractTreeBuilder.getBuilderFor(myTree);
    if (builder != null) {
      builder.queueUpdate();
    }
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(!TreeUtil.collectSelectedObjectsOfType(myTree, AntBuildGroupNodeDescriptor.class).isEmpty());
  }
}
