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
package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.AntBuildListener;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.lang.ant.config.execution.ExecutionHandler;
import com.intellij.lang.ant.config.impl.BuildFileProperty;
import com.intellij.lang.ant.dom.AntDomTarget;
import com.intellij.lang.ant.resources.AntActionsBundle;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.language.editor.PlatformDataKeys;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.psi.xml.XmlFile;
import consulo.xml.psi.xml.XmlTag;
import consulo.xml.util.xml.DomElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 28.05.12 16:07
 */
public class RunTargetAction extends AnAction {
  public RunTargetAction() {
    super();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Pair<AntBuildFileBase, AntDomTarget> antTarget = findAntTarget(e);
    if (antTarget == null) return;

    ExecutionHandler.runBuild(
      antTarget.first, new String[]{antTarget.second.getName().getValue()},
      e.getDataContext(),
      Collections.<BuildFileProperty>emptyList(),
      AntBuildListener.NULL);
  }


  @Override
  public void update(AnActionEvent e) {
    super.update(e);

    final Presentation presentation = e.getPresentation();

    Pair<AntBuildFileBase, AntDomTarget> antTarget = findAntTarget(e);
    if (antTarget == null) {
      presentation.setEnabled(false);
      presentation.setText(AntActionsBundle.message("action.RunTargetAction.text", ""));
    }
    else {
      presentation.setEnabled(true);
      presentation.setText(AntActionsBundle.message("action.RunTargetAction.text", "'" + antTarget.second.getName().getValue() + "'"));
    }
  }

  @Nullable
  private static Pair<AntBuildFileBase, AntDomTarget> findAntTarget(@Nonnull AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();

    final Editor editor = dataContext.getData(PlatformDataKeys.EDITOR);
    final Project project = dataContext.getData(PlatformDataKeys.PROJECT);

    if (project == null || editor == null) {
      return null;
    }
    final VirtualFile file = dataContext.getData(PlatformDataKeys.VIRTUAL_FILE);
    if (file == null) {
      return null;
    }

    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (!(psiFile instanceof XmlFile)) {
      return null;
    }
    final XmlFile xmlFile = (XmlFile)psiFile;

    final AntBuildFileBase antFile = AntConfigurationBase.getInstance(project).getAntBuildFile(xmlFile);
    if (antFile == null) {
      return null;
    }

    final PsiElement element = xmlFile.findElementAt(editor.getCaretModel().getOffset());
    if (element == null) {
      return null;
    }
    final XmlTag xmlTag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    if (xmlTag == null) {
      return null;
    }

    DomElement dom = AntSupport.getAntDomElement(xmlTag);
    while (dom != null && !(dom instanceof AntDomTarget)) {
      dom = dom.getParent();
    }

    final AntDomTarget domTarget = (AntDomTarget)dom;
    if (domTarget == null) {
      return null;
    }
    return Pair.create(antFile, domTarget);
  }
}
