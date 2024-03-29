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
package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.lang.ant.config.AntNoFileException;
import consulo.language.editor.PlatformDataKeys;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowId;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.awt.Messages;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.psi.xml.XmlDocument;
import consulo.xml.psi.xml.XmlFile;
import consulo.xml.psi.xml.XmlTag;

public class AddAntBuildFile extends AnAction
{
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(Project.KEY);
    consulo.virtualFileSystem.VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    AntConfiguration antConfiguration = AntConfiguration.getInstance(project);
    try {
      antConfiguration.addBuildFile(file);
      ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.ANT_BUILD).activate(null);
    }
    catch (AntNoFileException ex) {
      String message = ex.getMessage();
      if (message == null || message.length() == 0) {
        message = AntBundle.message("cannot.add.build.files.from.excluded.directories.error.message", ex.getFile().getPresentableUrl());
      }

      Messages.showWarningDialog(project, message, AntBundle.message("cannot.add.build.file.dialog.title"));
    }
  }

  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    Project project = e.getData(Project.KEY);
    if (project == null) {
      disable(presentation);
      return;
    }

    final VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    if (file == null) {
      disable(presentation);
      return;
    }

    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (!(psiFile instanceof XmlFile)) {
      disable(presentation);
      return;
    }

    final XmlFile xmlFile = (XmlFile)psiFile;
    final XmlDocument document = xmlFile.getDocument();
    if (document == null) {
      disable(presentation);
      return;
    }

    final XmlTag rootTag = document.getRootTag();
    if (rootTag == null) {
      disable(presentation);
      return;
    }

    if (!"project".equals(rootTag.getName())) {
      disable(presentation);
      return;
    }

    if (AntConfigurationBase.getInstance(project).getAntBuildFile(psiFile) != null) {
      disable(presentation);
      return;
    }

    enable(presentation);
  }

  private static void enable(Presentation presentation) {
    presentation.setEnabled(true);
    presentation.setVisible(true);
  }

  private static void disable(Presentation presentation) {
    presentation.setEnabled(false);
    presentation.setVisible(false);
  }
}

