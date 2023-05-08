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
package com.intellij.lang.ant.config.execution;

import com.intellij.lang.ant.config.AntBuildFile;
import consulo.apache.ant.execution.OutputBuilder;
import consulo.apache.ant.execution.OutputWatcher;
import consulo.application.progress.ProgressIndicator;
import consulo.build.ui.progress.BuildProgress;
import consulo.build.ui.progress.BuildProgressDescriptor;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.process.local.BaseProcessHandler;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;

import java.text.ParseException;

@Deprecated
final class OutputParser2 {

  public static OutputWatcher attachParser(Project myProject,
                                           AntProcessWrapper handler,
                                           ProgressIndicator progress,
                                           AntBuildFile buildFile,
                                           BuildProgress<BuildProgressDescriptor> buildProgress) {
    final OutputBuilder parser = new OutputBuilder(myProject,
                                                   (BaseProcessHandler<?>)handler.getProcessHandler(),
                                                   buildProgress
    );
    handler.addProcessListener(new ProcessListener() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        String text = event.getText().trim();
        try {
          ServiceMessage serviceMessage = ServiceMessage.parse(text);
          parser.onMessage(serviceMessage, text);
        }
        catch (ParseException ignored) {
          parser.onMessage(null, text);
        }
      }
    });
    return parser;
  }
}
