/*
 * Copyright 2013-2018 consulo.io
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
package consulo.apache.ant.config.impl.group;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.apache.ant.config.AntBuildFileGroup;

/**
 * @author VISTALL
 * @since 12:33/09.03.13
 */
public class AntBuildFileGroupImpl implements AntBuildFileGroup {
  private final String myName;
  private final AntBuildFileGroup myParent;
  private final List<AntBuildFileGroup> myChildren = new ArrayList<AntBuildFileGroup>(5);

  public AntBuildFileGroupImpl(String name, AntBuildFileGroup parent) {
    myName = name;
    myParent = parent;
  }

  @Nonnull
  @Override
  public String getName() {
    return myName;
  }

  @Nullable
  @Override
  public AntBuildFileGroup getParent() {
    return myParent;
  }

  @Nonnull
  @Override
  public AntBuildFileGroup[] getChildren() {
    return myChildren.toArray(AntBuildFileGroup.EMPTY_ARRAY);
  }

  @Nonnull
  public List<AntBuildFileGroup> getChildrenAsList() {
    return myChildren;
  }
}
