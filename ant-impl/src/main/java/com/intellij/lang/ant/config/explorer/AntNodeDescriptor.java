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

import consulo.ide.ui.CellAppearanceEx;
import consulo.project.Project;
import consulo.ui.ex.ColoredTextContainer;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.tree.NodeDescriptor;

import jakarta.annotation.Nonnull;

public abstract class AntNodeDescriptor extends NodeDescriptor implements CellAppearanceEx {
  protected final Project myProject;

  public AntNodeDescriptor(Project project, NodeDescriptor parentDescriptor) {
    super(parentDescriptor);
    myProject = project;
  }

  public abstract boolean isAutoExpand();

  @Override
  public void customize(@Nonnull ColoredTextContainer component) {
    component.setIcon(getIcon());
    component.append(toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  @Nonnull
  public String getText() {
    return toString();
  }
}
