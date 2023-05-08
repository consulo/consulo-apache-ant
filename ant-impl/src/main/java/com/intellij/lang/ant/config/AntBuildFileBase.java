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

import consulo.util.xml.serializer.WriteExternalException;
import consulo.component.util.config.AbstractProperty;
import consulo.util.xml.serializer.InvalidDataException;
import org.jdom.Element;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Map;

public interface AntBuildFileBase extends AntBuildFile {

  AntBuildModelBase getModel();

  @Nullable
  AntBuildModelBase getModelIfRegistered();

  AbstractProperty.AbstractPropertyContainer getAllOptions();

  boolean shouldExpand();
  
  void setShouldExpand(boolean expand);
  
  void updateProperties();

  void updateConfig();

  void setTreeView(final boolean value);

  void setVerboseMode(final boolean value);

  boolean isViewClosedWhenNoErrors();

  boolean isRunInBackground();

  void readWorkspaceProperties(final Element element) throws InvalidDataException;

  void writeWorkspaceProperties(final Element element) throws consulo.util.xml.serializer.WriteExternalException;

  void readProperties(final Element element) throws InvalidDataException;

  void writeProperties(final Element element) throws WriteExternalException;

  @Nonnull
  Map<String, String> getExternalProperties();
}
