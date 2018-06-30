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
package consulo.apache.ant.config;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

/**
 * @author VISTALL
 * @since 12:26/09.03.13
 */
public abstract class AntBuildFileGroupManager {
  public static AntBuildFileGroupManager getInstance(final Project project) {
    return ServiceManager.getService(project, AntBuildFileGroupManager.class);
  }

  @Nonnull
  public abstract AntBuildFileGroup createGroup(@Nullable AntBuildFileGroup parent, @Nonnull String name);

  public abstract void moveToGroup(@Nonnull AntBuildFile file, @Nullable AntBuildFileGroup group);

  public abstract AntBuildFile[] getFilesForGroup(@Nonnull AntBuildFileGroup group);

  @Nonnull
  public abstract AntBuildFileGroup[] getFirstLevelGroups();

  public abstract AntBuildFileGroup findGroup(@Nonnull AntBuildFile buildFile);

  public abstract void removeGroup(@Nonnull AntBuildFileGroup buildGroup);
}
