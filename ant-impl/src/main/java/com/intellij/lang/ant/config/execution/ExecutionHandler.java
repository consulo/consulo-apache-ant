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
package com.intellij.lang.ant.config.execution;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.AntBuildListener;
import com.intellij.lang.ant.config.impl.BuildFileProperty;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.Task;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.component.ProcessCanceledException;
import consulo.dataContext.DataContext;
import consulo.document.FileDocumentManager;
import consulo.execution.CantRunException;
import consulo.ide.impl.idea.execution.util.ExecutionErrorDialog;
import consulo.localHistory.LocalHistory;
import consulo.logging.Logger;
import consulo.pathMacro.Macro;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.project.Project;
import consulo.project.ui.wm.StatusBar;
import consulo.project.ui.wm.WindowManager;
import consulo.virtualFileSystem.encoding.EncodingProjectManager;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ExecutionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ant.execution.ExecutionHandler");

  @NonNls
  public static final String PARSER_JAR = "xerces1.jar";

  private ExecutionHandler() {
  }

  /**
   * @param antBuildListener should not be null. Use {@link com.intellij.lang.ant.config.AntBuildListener#NULL}
   */
  public static void runBuild(final AntBuildFileBase buildFile,
                              String[] targets,
                              @Nullable final AntBuildMessageView buildMessageViewToReuse,
                              final DataContext dataContext,
                              List<BuildFileProperty> additionalProperties, @Nonnull final AntBuildListener antBuildListener) {
    FileDocumentManager.getInstance().saveAllDocuments();
    final AntCommandLineBuilder builder = new AntCommandLineBuilder();
    final AntBuildMessageView messageView;
    final GeneralCommandLine commandLine;
    synchronized (builder) {
      Project project = buildFile.getProject();

      try {
        builder.setBuildFile(buildFile.getAllOptions(), VirtualFileUtil.virtualToIoFile(buildFile.getVirtualFile()));
        builder.calculateProperties(dataContext, additionalProperties);
        builder.addTargets(targets);

        builder.getJavaParameters().setCharset(EncodingProjectManager.getInstance(buildFile.getProject()).getDefaultCharset());

        messageView = prepareMessageView(buildMessageViewToReuse, buildFile, targets);
        commandLine = builder.getJavaParameters().toCommandLine();
        messageView.setBuildCommandLine(commandLine.getCommandLineString());
      }
      catch (RunCanceledException e) {
        e.showMessage(project, AntBundle.message("run.ant.erorr.dialog.title"));
        antBuildListener.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
        return;
      }
      catch (CantRunException e) {
        ExecutionErrorDialog.show(e, AntBundle.message("cant.run.ant.erorr.dialog.title"), project);
        antBuildListener.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
        return;
      }
      catch (Macro.ExecutionCancelledException e) {
        antBuildListener.buildFinished(AntBuildListener.ABORTED, 0);
        return;
      }
      catch (Throwable e) {
        antBuildListener.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
        LOG.error(e);
        return;
      }
    }

    final boolean startInBackground = buildFile.isRunInBackground();

    new Task.Backgroundable(buildFile.getProject(), AntBundle.message("ant.build.progress.dialog.title"), true) {

      public boolean shouldStartInBackground() {
        return startInBackground;
      }

      public void run(@Nonnull final ProgressIndicator indicator) {
        try {
          runBuild(indicator, messageView, buildFile, antBuildListener, commandLine);
        }
        catch (Throwable e) {
          LOG.error(e);
          antBuildListener.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
        }
      }
    }.queue();
  }

  private static void runBuild(final ProgressIndicator progress,
                               @Nonnull final AntBuildMessageView errorView,
                               @Nonnull final AntBuildFile buildFile,
                               @Nonnull final AntBuildListener antBuildListener,
                               @Nonnull GeneralCommandLine commandLine) {
    final Project project = buildFile.getProject();

    final long startTime = System.currentTimeMillis();
    LocalHistory.getInstance().putSystemLabel(project, AntBundle.message("ant.build.local.history.label", buildFile.getName()));
    final AntProcessWrapper handler;
    try {
      handler = AntProcessWrapper.runCommandLine(commandLine);
    }
    catch (final ExecutionException e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          ExecutionErrorDialog.show(e, AntBundle.message("could.not.start.process.erorr.dialog.title"), project);
        }
      });
      antBuildListener.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
      return;
    }

    processRunningAnt(progress, handler, errorView, buildFile, startTime, antBuildListener);
    handler.waitFor();
  }

  private static void processRunningAnt(final ProgressIndicator progress,
                                        final AntProcessWrapper wrapper,
                                        final AntBuildMessageView errorView,
                                        final AntBuildFile buildFile,
                                        final long startTime,
                                        final AntBuildListener antBuildListener) {
    final Project project = buildFile.getProject();
    final StatusBar statusbar = WindowManager.getInstance().getStatusBar(project);
    if (statusbar != null) {
      statusbar.setInfo(AntBundle.message("ant.build.started.status.message"));
    }

    final CheckCancelTask checkCancelTask = new CheckCancelTask(progress, wrapper.getProcessHandler());
    checkCancelTask.start(0);

    final OutputParser parser = OutputParser2.attachParser(project, wrapper, errorView, progress, buildFile);

    wrapper.addProcessListener(new ProcessListener() {
      public void processTerminated(ProcessEvent event) {
        final long buildTime = System.currentTimeMillis() - startTime;
        checkCancelTask.cancel();
        parser.setStopped(true);

        //TODO
//        final OutputPacketProcessor dispatcher = wrapper.getErr().getEventsDispatcher();
//        errorView.buildFinished(progress != null && progress.isCanceled(), buildTime, antBuildListener, dispatcher);
//        ApplicationManager.getApplication().invokeLater(new Runnable() {
//          public void run() {
//            if (project.isDisposed()) {
//              return;
//            }
//            errorView.removeProgressPanel();
//            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
//            if (toolWindow != null) { // can be null if project is closed
//              toolWindow.activate(null, false);
//            }
//          }
//        }, Application.get().getNoneModalityState());
      }
    });
    wrapper.startNotify();
  }

  static final class CheckCancelTask implements Runnable {
    private final ProgressIndicator myProgressIndicator;
    private final ProcessHandler myProcessHandler;
    private volatile boolean myCanceled;

    public CheckCancelTask(ProgressIndicator progressIndicator, ProcessHandler process) {
      myProgressIndicator = progressIndicator;
      myProcessHandler = process;
    }

    public void cancel() {
      myCanceled = true;
    }

    public void run() {
      if (!myCanceled) {
        try {
          myProgressIndicator.checkCanceled();
          start(50);
        }
        catch (ProcessCanceledException e) {
          myProcessHandler.destroyProcess();
        }
      }
    }

    public void start(final long delay) {
      AppExecutorUtil.getAppScheduledExecutorService().schedule(this, delay, TimeUnit.MILLISECONDS);
    }
  }

  private static AntBuildMessageView prepareMessageView(@Nullable AntBuildMessageView buildMessageViewToReuse,
                                                        AntBuildFileBase buildFile,
                                                        String[] targets) throws RunCanceledException {
    AntBuildMessageView messageView;
    if (buildMessageViewToReuse != null) {
      messageView = buildMessageViewToReuse;
      messageView.emptyAll();
    }
    else {
      messageView = AntBuildMessageView.openBuildMessageView(buildFile.getProject(), buildFile, targets);
      if (messageView == null) {
        throw new RunCanceledException(AntBundle.message("canceled.by.user.error.message"));
      }
    }
    return messageView;
  }
}
