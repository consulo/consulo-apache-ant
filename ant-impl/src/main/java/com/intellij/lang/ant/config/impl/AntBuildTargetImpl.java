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
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.execution.ExecutionHandler;
import com.intellij.lang.ant.dom.AntDomElement;
import com.intellij.lang.ant.dom.AntDomProject;
import com.intellij.lang.ant.dom.AntDomRecursiveVisitor;
import com.intellij.lang.ant.dom.AntDomTarget;
import consulo.dataContext.DataContext;
import consulo.ide.impl.idea.util.StringBuilderSpinAllocator;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.navigation.OpenFileDescriptor;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.util.xml.DomTarget;

import jakarta.annotation.Nullable;
import java.util.List;

public class AntBuildTargetImpl implements AntBuildTargetBase {

  private final AntBuildModelBase myModel;
  private final VirtualFile myFile;
  private final boolean myIsDefault;
  private final int myHashCode;
  private final String myName;
  private final String myDisplayName;
  private final String myDescription;
  private final Project myProject;
  private final int myTextOffset;

  public AntBuildTargetImpl(final AntDomTarget target,
                            final AntBuildModelBase buildModel,
                            final consulo.virtualFileSystem.VirtualFile sourceFile,
                            final boolean isImported,
                            final boolean isDefault) {
    myModel = buildModel;
    myFile = sourceFile;
    myIsDefault = isDefault;
    myHashCode = target.hashCode();
    myName = target.getName().getRawText();
    String name = target.getName().getRawText();
    if (isImported) {
      final String projectName = target.getAntProject().getName().getRawText();
      name = projectName + "" + name;
    }
    myDisplayName = name;
    myProject = target.getManager().getProject();
    final DomTarget domTarget = DomTarget.getTarget(target);
    if (domTarget != null) {
      myTextOffset = domTarget.getTextOffset();
    }
    else {
      myTextOffset = target.getXmlTag().getTextOffset();
    }

    final String desc = target.getDescription().getRawText();
    myDescription = (desc != null && desc.trim().length() > 0) ? desc : null;
  }

  public int hashCode() {
    return myHashCode;
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof AntBuildTargetImpl)) {
      return false;
    }
    final AntBuildTargetImpl that = (AntBuildTargetImpl)obj;
    return Comparing.equal(myName, that.myName) && Comparing.equal(myFile, that.myFile);
  }

  public Project getProject() {
    return myProject;
  }

  @Nullable
  public String getName() {
    return myName;
  }

  @Nullable
  public String getDisplayName() {
    return myDisplayName;
  }

  @Nullable
  public String getNotEmptyDescription() {
    return myDescription;
  }

  public boolean isDefault() {
    return myIsDefault;
  }

  public consulo.virtualFileSystem.VirtualFile getContainingFile() {
    return myFile;
  }

  public AntBuildModelBase getModel() {
    return myModel;
  }

  @Nullable
  public String getActionId() {
    final String modelName = myModel.getName();
    if (modelName == null || modelName.length() == 0) {
      return null;
    }
    final StringBuilder name = consulo.ide.impl.idea.util.StringBuilderSpinAllocator.alloc();
    try {
      name.append(AntConfiguration.getActionIdPrefix(myModel.getBuildFile().getProject()));
      name.append("_");
      name.append(modelName);
      name.append('_');
      name.append(getName());
      return name.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(name);
    }
  }

  @Nullable
  public BuildTask findTask(final String taskName) {
    final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(myFile);
    final AntDomProject domProject = AntSupport.getAntDomProject(psiFile);
    if (domProject != null) {
      final AntDomTarget antTarget = domProject.findDeclaredTarget(myName);
      if (antTarget != null) {
        final Ref<AntDomElement> result = new Ref<AntDomElement>(null);
        antTarget.accept(new AntDomRecursiveVisitor() {
          public void visitAntDomElement(AntDomElement element) {
            if (result.get() != null) {
              return;
            }
            if (element.isTask() && taskName.equals(element.getXmlElementName())) {
              result.set(element);
              return;
            }
            super.visitAntDomElement(element);
          }
        });
        final AntDomElement task = result.get();
        if (task != null) {
          return new BuildTask(this, task);
        }
      }
    }
    return null;
  }

  public OpenFileDescriptor getOpenFileDescriptor() {
    return (myFile == null) ? null : OpenFileDescriptorFactory.getInstance(myProject).builder(myFile).offset(myTextOffset).build();
  }

  public void run(DataContext dataContext, List<BuildFileProperty> additionalProperties, AntBuildListener buildListener) {
    AntBuildModel model = getModel();
    if (model == null) {
      buildListener.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
      throw new IllegalStateException("Target '" + getName() + "' is invalid: model is null");
    }
    AntBuildFile buildFile = model.getBuildFile();
    if (buildFile == null) {
      buildListener.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
      throw new IllegalStateException("Target '" + getName() + "' is invalid: build file is null");
    }

    String[] targets = isDefault() ? ArrayUtil.EMPTY_STRING_ARRAY : new String[]{getName()};
    ExecutionHandler.runBuild((AntBuildFileBase)buildFile, targets, dataContext, additionalProperties, buildListener);
  }
}
