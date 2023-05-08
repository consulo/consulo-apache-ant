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
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.AntBuildListener;
import com.intellij.lang.ant.config.execution.ExecutionHandler;
import consulo.application.AllIcons;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;

import java.util.List;

public final class RunAction extends AnAction {
  private final AntBuildFileBase myBuildFile;
  private final String[] myTargets;

  public RunAction(AntBuildFileBase buildFile, String[] targets) {
    super(AntBundle.message("rerun.ant.action.name"), null, AllIcons.Actions.Rerun);
    myBuildFile = buildFile;
    myTargets = targets;
  }

  public void actionPerformed(AnActionEvent e) {
    ExecutionHandler.runBuild(
      myBuildFile,
      myTargets,
      e.getDataContext(), List.of(), AntBuildListener.NULL);
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(true);
  }
}
