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

import consulo.application.ApplicationManager;
import consulo.execution.ui.console.*;
import consulo.process.NopProcessHandler;
import consulo.process.ProcessHandler;
import consulo.process.ProcessOutputTypes;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nullable;
import javax.swing.*;
import java.io.File;

public final class PlainTextView implements AntOutputView {

  private final ConsoleView myConsole;
  private final Project myProject;
  private String myCommandLine;
  private final ProcessHandler myProcessHandler = new NopProcessHandler();

  public PlainTextView(Project project) {
    myProject = project;
    TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    builder.addFilter(new AntMessageFilter());
    builder.addFilter(new JUnitFilter());
    myConsole = builder.getConsole();
    myConsole.attachToProcess(myProcessHandler);
  }

  public void dispose() {
    myConsole.dispose();
  }

  @Override
  public JComponent getComponent() {
    return myConsole.getComponent();
  }

  @Override
  @Nullable
  public Object addMessage(AntMessage message) {
    print(message.getText() + "\n", ProcessOutputTypes.STDOUT);
    return null;
  }

  private void print(String text, Key type) {
    myProcessHandler.notifyTextAvailable(text, type);
  }

  public void addMessages(AntMessage[] messages) {
    for (AntMessage message : messages) {
      addMessage(message);
    }
  }

  @Override
  public void addJavacMessage(AntMessage message, String url) {
    final consulo.virtualFileSystem.VirtualFile file = message.getFile();
    if (message.getLine() > 0) {
      final StringBuilder builder = new StringBuilder();
      if (file != null) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            String presentableUrl = file.getPresentableUrl();
            builder.append(presentableUrl);
            builder.append(' ');
          }
        });
      }
      else if (url != null) {
        builder.append(url);
        builder.append(' ');
      }
      builder.append('(');
      builder.append(message.getLine());
      builder.append(':');
      builder.append(message.getColumn());
      builder.append(")");
      print(builder.toString(), ProcessOutputTypes.STDOUT);

    }
    print(message.getText(), ProcessOutputTypes.STDOUT);
  }

  @Override
  public void addException(AntMessage exception, boolean showFullTrace) {
    String text = exception.getText();
    if (!showFullTrace) {
      int index = text.indexOf("\r\n");
      if (index != -1) {
        text = text.substring(0, index) + "\n";
      }
    }
    print(text, ProcessOutputTypes.STDOUT);
  }

  public void clearAllMessages() {
    myConsole.clear();
  }

  @Override
  public void startBuild(AntMessage message) {
    print(myCommandLine + "\n", ProcessOutputTypes.SYSTEM);
    addMessage(message);
  }

  @Override
  public void buildFailed(AntMessage message) {
    print(myCommandLine + "\n", ProcessOutputTypes.SYSTEM);
    addMessage(message);
  }

  @Override
  public void startTarget(AntMessage message) {
    addMessage(message);
  }

  @Override
  public void startTask(AntMessage message) {
    addMessage(message);
  }

  @Override
  public void finishBuild(String messageText) {
    print("\n" + messageText + "\n", ProcessOutputTypes.SYSTEM);
  }

  @Override
  public void finishTarget() {
  }

  @Override
  public void finishTask() {
  }

  @Override
  @Nullable
  public Object getData(Key<?> dataId) {
    return null;
  }

  public void setBuildCommandLine(String commandLine) {
    myCommandLine = commandLine;
  }

  private final class JUnitFilter implements Filter {
    @Override
    @Nullable
    public Result applyFilter(String line, int entireLength) {
      HyperlinkUtil.PlaceInfo placeInfo = HyperlinkUtil.parseJUnitMessage(myProject, line);
      if (placeInfo == null) {
        return null;
      }

      int textStartOffset = entireLength - line.length();
      int highlightStartOffset = textStartOffset + placeInfo.getLinkStartIndex();
      int highlightEndOffset = textStartOffset + placeInfo.getLinkEndIndex() + 1;

      OpenFileHyperlinkInfo info = new OpenFileHyperlinkInfo(myProject, placeInfo.getFile(), placeInfo.getLine(), placeInfo.getColumn());
      return new Result(highlightStartOffset, highlightEndOffset, info);
    }
  }

  private final class AntMessageFilter implements Filter {
    @Override
    public Result applyFilter(String line, int entireLength) {
      int afterLineNumberIndex = line.indexOf(": "); // end of file_name_and_line_number sequence
      if (afterLineNumberIndex == -1) {
        return null;
      }

      String fileAndLineNumber = line.substring(0, afterLineNumberIndex);
      int index = fileAndLineNumber.lastIndexOf(':');

      if (index == -1) {
        return null;
      }

      final String fileName = fileAndLineNumber.substring(0, index);
      String lineNumberStr = fileAndLineNumber.substring(index + 1, fileAndLineNumber.length()).trim();
      int lineNumber;
      try {
        lineNumber = Integer.parseInt(lineNumberStr);
      }
      catch (NumberFormatException e) {
        return null;
      }

      final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(fileName.replace(File.separatorChar, '/'));
      if (file == null) {
        return null;
      }

      int textStartOffset = entireLength - line.length();
      int highlightEndOffset = textStartOffset + afterLineNumberIndex;

      OpenFileHyperlinkInfo info = new OpenFileHyperlinkInfo(myProject, file, lineNumber - 1);
      return new Result(textStartOffset, highlightEndOffset, info);
    }
  }
}
