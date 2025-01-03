/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.AntBuildModelBase;
import consulo.apache.ant.ApacheAntIcons;
import consulo.ide.impl.idea.openapi.roots.ui.util.CompositeAppearance;
import consulo.project.Project;
import consulo.ui.ex.ColoredTextContainer;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.tree.NodeDescriptor;
import consulo.util.lang.Comparing;

import jakarta.annotation.Nonnull;

public final class AntBuildFileNodeDescriptor extends AntNodeDescriptor {

  private final AntBuildFileBase myBuildFile;
  private consulo.ide.impl.idea.openapi.roots.ui.util.CompositeAppearance myAppearance;

  public AntBuildFileNodeDescriptor(Project project, NodeDescriptor parentDescriptor, AntBuildFileBase buildFile) {
    super(project, parentDescriptor);
    myBuildFile = buildFile;
  }

  public Object getElement() {
    return myBuildFile;
  }

  public AntBuildFile getBuildFile() {
    return myBuildFile;
  }

  public boolean update() {
    setIcon(ApacheAntIcons.AntInstallation);

    CompositeAppearance oldAppearance = myAppearance;
    myAppearance = new consulo.ide.impl.idea.openapi.roots.ui.util.CompositeAppearance();
    myAppearance.getEnding().addText(myBuildFile.getPresentableName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    myAppearance.setIcon(getIcon());
    final AntBuildModelBase buildModel = myBuildFile.getModelIfRegistered();
    if (buildModel != null) {
      AntTargetNodeDescriptor.addShortcutText(buildModel.getDefaultTargetActionId(), myAppearance);
    }
    myName = myBuildFile.getPresentableName();
    return !Comparing.equal(myAppearance, oldAppearance);
  }

  public void customize(@Nonnull ColoredTextContainer component) {
    if (myAppearance != null) {
      myAppearance.customize(component);
    }
    else {
      super.customize(component);
    }
  }

  public boolean isAutoExpand() {
    return myBuildFile.shouldExpand();
  }
}
