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

package com.intellij.lang.ant.config;

import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.psi.xml.XmlFile;

import jakarta.annotation.Nullable;

public interface AntBuildFile {

  AntBuildFile[] EMPTY_ARRAY = new AntBuildFile[0];

  @Nullable
  String getPresentableName();

  @Nullable
  String getName();

  AntBuildModel getModel();

  @Nullable
  XmlFile getAntFile();

  Project getProject();

  @Nullable
  VirtualFile getVirtualFile();

  @Nullable
  String getPresentableUrl();

  boolean isTargetVisible(final AntBuildTarget target);

  boolean exists();
}
