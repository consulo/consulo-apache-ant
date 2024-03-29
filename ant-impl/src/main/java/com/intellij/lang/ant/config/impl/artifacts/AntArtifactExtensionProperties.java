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
package com.intellij.lang.ant.config.impl.artifacts;

import com.intellij.lang.ant.config.impl.BuildFileProperty;
import consulo.util.xml.serializer.annotation.Attribute;
import consulo.util.xml.serializer.annotation.Tag;
import consulo.util.xml.serializer.annotation.AbstractCollection;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class AntArtifactExtensionProperties {
  @Tag("file")
  public String myFileUrl;

  @Tag("target")
  public String myTargetName;

  @Attribute("enabled")
  public boolean myEnabled;

  @consulo.util.xml.serializer.annotation.Tag("build-properties")
  @AbstractCollection(surroundWithTag = false)
  public List<BuildFileProperty> myUserProperties = new ArrayList<BuildFileProperty>();
}
