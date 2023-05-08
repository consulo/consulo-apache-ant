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
package com.intellij.lang.ant.config.impl.artifacts;

import consulo.annotation.component.ExtensionImpl;
import consulo.compiler.artifact.ArtifactProperties;
import consulo.compiler.artifact.ArtifactPropertiesProvider;
import consulo.compiler.artifact.ArtifactType;

import javax.annotation.Nonnull;

/**
 * @author nik
 */
@ExtensionImpl
public class AntArtifactPostprocessingPropertiesProvider extends ArtifactPropertiesProvider {
  public static AntArtifactPostprocessingPropertiesProvider getInstance() {
    return EP_NAME.findExtension(AntArtifactPostprocessingPropertiesProvider.class);
  }

  public AntArtifactPostprocessingPropertiesProvider() {
    super("ant-postprocessing");
  }

  @Nonnull
  public ArtifactProperties<?> createProperties(@Nonnull ArtifactType artifactType) {
    return new AntArtifactProperties(true);
  }

}
