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

import com.intellij.java.compiler.impl.javaCompiler.FileObject;
import com.intellij.java.compiler.impl.javaCompiler.javac.JavacOutputParser;
import com.intellij.lang.ant.AntBundle;
import consulo.apache.ant.execution.OutputWatcher;
import consulo.apache.ant.rt.common.AntLoggerConstants;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.compiler.CompilerMessageCategory;
import consulo.logging.Logger;
import consulo.process.local.BaseProcessHandler;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

//import com.intellij.compiler.impl.javaCompiler.jikes.JikesOutputParser;

public class OutputParser implements OutputWatcher {

  @NonNls private static final String JAVAC = "javac";
  @NonNls private static final String ECHO = "echo";

  private static final Logger LOG = Logger.getInstance("#com.intellij.ant.execution.OutputParser");
  private final Project myProject;
  private final OldAntBuildMessageView myMessageView;
  private final WeakReference<ProgressIndicator> myProgress;
  private final String myBuildName;
  private final BaseProcessHandler<?> myProcessHandler;
  private boolean isStopped;
  private List<String> myJavacMessages;
  private boolean myFirstLineProcessed;
  private boolean myStartedSuccessfully;
  private boolean myIsEcho;

  public OutputParser(Project project,
                      BaseProcessHandler<?> processHandler,
                      OldAntBuildMessageView errorsView,
                      ProgressIndicator progress,
                      String buildName) {
    myProject = project;
    myProcessHandler = processHandler;
    myMessageView = errorsView;
    myProgress = new WeakReference<ProgressIndicator>(progress);
    myBuildName = buildName;
    myMessageView.setParsingThread(this);
  }

  @Override
  public final void stopProcess() {
    myProcessHandler.destroyProcess();
  }

  @Override
  public boolean isTerminateInvoked() {
    return myProcessHandler.isProcessTerminating();
  }

  protected Project getProject() {
    return myProject;
  }

  protected BaseProcessHandler<?> getProcessHandler() {
    return myProcessHandler;
  }

  @Override
  public final boolean isStopped() {
    return isStopped;
  }

  @Override
  public final void setStopped(boolean stopped) {
    isStopped = stopped;
  }

  @Override
  public int getErrorsCount() {
    return 0;
  }

  private void setProgressStatistics(String s) {
    final ProgressIndicator progress = myProgress.get();
    if (progress != null) {
      progress.setText2(s);
    }
  }

  private void setProgressText(String s) {
    final ProgressIndicator progress = myProgress.get();
    if (progress != null) {
      progress.setText(s);
    }
  }

  private void printRawError(String text) {
    myMessageView.outputError(text, 0);
  }

  public final void readErrorOutput(String text) {
    if (!myFirstLineProcessed) {
      myFirstLineProcessed = true;
      myStartedSuccessfully = false;
      myMessageView.buildFailed(myBuildName);
    }
    if (!myStartedSuccessfully) {
      printRawError(text);
    }
  }


  protected final void processTag(char tagName, final String tagValue, final int priority) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.valueOf(tagName) + priority + "=" + tagValue);
    }

    if (AntLoggerConstants.TARGET == tagName) {
      setProgressStatistics(AntBundle.message("target.tag.name.status.text", tagValue));
    }
    else if (AntLoggerConstants.TASK == tagName) {
      setProgressText(AntBundle.message("executing.task.tag.value.status.text", tagValue));
      if (JAVAC.equals(tagValue)) {
        myJavacMessages = new ArrayList<String>();
      }
      else if (ECHO.equals(tagValue)) {
        myIsEcho = true;
      }
    }

    if (myJavacMessages != null && (AntLoggerConstants.MESSAGE == tagName || AntLoggerConstants.ERROR == tagName)) {
      myJavacMessages.add(tagValue);
      return;
    }

    if (AntLoggerConstants.MESSAGE == tagName) {
      if (myIsEcho) {
        myMessageView.outputMessage(tagValue, OldAntBuildMessageView.PRIORITY_VERBOSE);
      }
      else {
        myMessageView.outputMessage(tagValue, priority);
      }
    }
    else if (AntLoggerConstants.TARGET == tagName) {
      myMessageView.startTarget(tagValue);
    }
    else if (AntLoggerConstants.TASK == tagName) {
      myMessageView.startTask(tagValue);
    }
    else if (AntLoggerConstants.ERROR == tagName) {
      myMessageView.outputError(tagValue, priority);
    }
    else if (AntLoggerConstants.EXCEPTION == tagName) {
      String exceptionText = tagValue.replace(AntLoggerConstants.EXCEPTION_LINE_SEPARATOR, '\n');
      myMessageView.outputException(exceptionText);
    }
    else if (AntLoggerConstants.BUILD == tagName) {
      myMessageView.startBuild(myBuildName);
    }
    else if (AntLoggerConstants.TARGET_END == tagName || AntLoggerConstants.TASK_END == tagName) {
      final List<String> javacMessages = myJavacMessages;
      myJavacMessages = null;
      processJavacMessages(javacMessages, myMessageView, myProject);
      myIsEcho = false;
      if (AntLoggerConstants.TARGET_END == tagName) {
        myMessageView.finishTarget();
      }
      else {
        myMessageView.finishTask();
      }
    }
  }

  private static boolean isJikesMessage(String errorMessage) {
    for (int j = 0; j < errorMessage.length(); j++) {
      if (errorMessage.charAt(j) == ':') {
        int offset = getNextTwoPoints(j, errorMessage);
        if (offset < 0) {
          continue;
        }
        offset = getNextTwoPoints(offset, errorMessage);
        if (offset < 0) {
          continue;
        }
        offset = getNextTwoPoints(offset, errorMessage);
        if (offset < 0) {
          continue;
        }
        offset = getNextTwoPoints(offset, errorMessage);
        if (offset >= 0) {
          return true;
        }
      }
    }
    return false;
  }

  private static int getNextTwoPoints(int offset, String message) {
    for (int i = offset + 1; i < message.length(); i++) {
      char c = message.charAt(i);
      if (c == ':') {
        return i;
      }
      if (Character.isDigit(c)) {
        continue;
      }
      return -1;
    }
    return -1;
  }

  private static void processJavacMessages(final List<String> javacMessages, final OldAntBuildMessageView messageView, Project project) {
    if (javacMessages == null) return;

    boolean isJikes = false;
    for (String errorMessage : javacMessages) {
      if (isJikesMessage(errorMessage)) {
        isJikes = true;
        break;
      }
    }

    com.intellij.java.compiler.impl.OutputParser outputParser;
    if (isJikes) {
      outputParser = null;
    //  outputParser = new JikesOutputParser(project);
    }
    else {
      outputParser = new JavacOutputParser(project);
    }

    com.intellij.java.compiler.impl.OutputParser.Callback callback = new com.intellij.java.compiler.impl.OutputParser.Callback() {
      private int myIndex = -1;

      @Override
      @Nullable
      public String getCurrentLine() {
        if (javacMessages == null || myIndex >= javacMessages.size()) {
          return null;
        }
        return javacMessages.get(myIndex);
      }

      @Override
      public String getNextLine() {
        final int size = javacMessages.size();
        final int next = Math.min(myIndex + 1, javacMessages.size());
        myIndex = next;
        if (next >= size) {
          return null;
        }
        return javacMessages.get(next);
      }

      @Override
      public void pushBack(String line) {
        myIndex--;
      }

      @Override
      public void message(final CompilerMessageCategory category,
                          final String message,
                          final String url,
                          final int lineNum,
                          final int columnNum) {
        consulo.util.lang.text.StringTokenizer tokenizer = new consulo.util.lang.text.StringTokenizer(message, "\n", false);
        final String[] strings = new String[tokenizer.countTokens()];
        //noinspection ForLoopThatDoesntUseLoopVariable
        for (int idx = 0; tokenizer.hasMoreTokens(); idx++) {
          strings[idx] = tokenizer.nextToken();
        }
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            VirtualFile file = url == null ? null : VirtualFileManager.getInstance().findFileByUrl(url);
            messageView.outputJavacMessage(convertCategory(category), strings, file, url, lineNum, columnNum);
          }
        });
      }

      @Override
      public void setProgressText(String text) {
      }

      @Override
      public void fileProcessed(String path) {
      }

      @Override
      public void fileGenerated(FileObject path) {
      }
    };
    try {
      while (true) {
        if (!outputParser.processMessageLine(callback)) {
          break;
        }
      }
    }
    catch (Exception e) {
      //ignore
    }
  }

  private static OldAntBuildMessageView.MessageType convertCategory(consulo.compiler.CompilerMessageCategory category) {
    if (consulo.compiler.CompilerMessageCategory.ERROR.equals(category)) {
      return OldAntBuildMessageView.MessageType.ERROR;
    }
    return OldAntBuildMessageView.MessageType.MESSAGE;
  }

}
