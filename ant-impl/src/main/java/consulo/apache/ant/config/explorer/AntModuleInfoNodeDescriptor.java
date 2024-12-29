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
package consulo.apache.ant.config.explorer;

import com.intellij.lang.ant.config.explorer.AntNodeDescriptor;
import consulo.module.Module;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.ex.ColoredTextContainer;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.tree.NodeDescriptor;

import jakarta.annotation.Nonnull;

/**
* @author VISTALL
* @date 14:08/08.03.13
*/
public final class AntModuleInfoNodeDescriptor extends AntNodeDescriptor {
  private Module myModule;

  public AntModuleInfoNodeDescriptor(Project project, NodeDescriptor parentDescriptor, Module module) {
    super(project, parentDescriptor);
    myModule = module;
    myName = module.getName();
    setIcon(PlatformIconGroup.nodesModule());
  }

  @Override
  public boolean isAutoExpand() {
    return false;
  }

  @Override
  public boolean update() {
    return true;
  }

  @Override
  public Object getElement() {
    return myModule;
  }

  @Override
  public void customize(@Nonnull ColoredTextContainer component) {
    component.append(toString(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    component.setIcon(getIcon());
  }
}
