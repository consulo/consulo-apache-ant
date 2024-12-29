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

import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildModel;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.impl.AntConfigurationImpl;
import com.intellij.lang.ant.config.impl.BuildFileProperty;
import consulo.compiler.CompilerMessageCategory;
import consulo.compiler.artifact.ArtifactProperties;
import consulo.compiler.artifact.ui.ArtifactEditorContext;
import consulo.compiler.artifact.ui.ArtifactPropertiesEditor;
import consulo.dataContext.DataContext;
import consulo.ide.impl.idea.openapi.actionSystem.impl.SimpleDataContext;
import consulo.compiler.CompileContext;
import consulo.compiler.artifact.Artifact;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
* @author nik
*/
public class AntArtifactProperties extends ArtifactProperties<AntArtifactExtensionProperties>
{
  @NonNls
  public static final String ARTIFACT_OUTPUT_PATH_PROPERTY = "artifact.output.path";

  private AntArtifactExtensionProperties myExtensionProperties = new AntArtifactExtensionProperties();
  private boolean myPostProcessing;

  public AntArtifactProperties(boolean postProcessing) {
    myPostProcessing = postProcessing;
  }

  public ArtifactPropertiesEditor createEditor(@Nonnull ArtifactEditorContext context) {
    return new AntArtifactPropertiesEditor(this, context, myPostProcessing);
  }

  public AntArtifactExtensionProperties getState() {
    return myExtensionProperties;
  }

  @Override
  public void onBuildStarted(@Nonnull consulo.compiler.artifact.Artifact artifact, @Nonnull consulo.compiler.CompileContext compileContext) {
    if (!myPostProcessing) {
      runAntTarget(compileContext, artifact);
    }
  }

  @Override
  public void onBuildFinished(@Nonnull consulo.compiler.artifact.Artifact artifact, @Nonnull final CompileContext compileContext) {
    if (myPostProcessing) {
      runAntTarget(compileContext, artifact);
    }
  }

  private void runAntTarget(CompileContext compileContext, final consulo.compiler.artifact.Artifact artifact) {
    if (myExtensionProperties.myEnabled) {
      final Project project = compileContext.getProject();
      final AntBuildTarget target = findTarget(AntConfiguration.getInstance(project));
      if (target != null) {
        final DataContext dataContext = SimpleDataContext.getProjectContext(project);
        List<BuildFileProperty> properties = getAllProperties(artifact);
        final boolean success = AntConfigurationImpl.executeTargetSynchronously(dataContext, target, properties);
        if (!success) {
          compileContext.addMessage(CompilerMessageCategory.ERROR, "Cannot build artifact '" + artifact.getName() + "': ant target '" + target.getDisplayName() + "' failed with error", null, -1, -1);
        }
      }
    }
  }

  public void loadState(AntArtifactExtensionProperties state) {
    myExtensionProperties = state;
  }

  public String getFileUrl() {
    return myExtensionProperties.myFileUrl;
  }

  public String getTargetName() {
    return myExtensionProperties.myTargetName;
  }

  public boolean isEnabled() {
    return myExtensionProperties.myEnabled;
  }

  public List<BuildFileProperty> getUserProperties() {
    return myExtensionProperties.myUserProperties;
  }

  public void setUserProperties(List<BuildFileProperty> userProperties) {
    myExtensionProperties.myUserProperties = userProperties;
  }

  public void setEnabled(boolean enabled) {
    myExtensionProperties.myEnabled = enabled;
  }

  public void setFileUrl(String fileUrl) {
    myExtensionProperties.myFileUrl = fileUrl;
  }

  public void setTargetName(String targetName) {
    myExtensionProperties.myTargetName = targetName;
  }

  @Nullable
  public AntBuildTarget findTarget(final AntConfiguration antConfiguration) {
    String fileUrl = getFileUrl();
    String targetName = getTargetName();
    if (fileUrl == null || targetName == null) return null;

    final AntBuildFile[] buildFiles = antConfiguration.getBuildFiles();
    for (AntBuildFile buildFile : buildFiles) {
      final VirtualFile file = buildFile.getVirtualFile();
      if (file != null && file.getUrl().equals(fileUrl)) {
        final AntBuildModel buildModel = buildFile.getModel();
        return buildModel != null ? buildModel.findTarget(targetName) : null;
      }
    }
    return null;
  }

  public List<BuildFileProperty> getAllProperties(@Nonnull Artifact artifact) {
    final List<BuildFileProperty> properties = new ArrayList<BuildFileProperty>();
    properties.add(new BuildFileProperty(ARTIFACT_OUTPUT_PATH_PROPERTY, artifact.getOutputPath()));
    properties.addAll(myExtensionProperties.myUserProperties);
    return properties;
  }

  public static boolean isPredefinedProperty(String propertyName) {
    return ARTIFACT_OUTPUT_PATH_PROPERTY.equals(propertyName);
  }
}
