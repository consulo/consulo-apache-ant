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
import com.intellij.lang.ant.config.actions.RunAction;
import com.intellij.lang.ant.config.impl.BuildFileProperty;
import consulo.apache.ant.execution.OutputWatcher;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.Task;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.build.ui.BuildDescriptor;
import consulo.build.ui.BuildViewManager;
import consulo.build.ui.DefaultBuildDescriptor;
import consulo.build.ui.progress.BuildProgress;
import consulo.build.ui.progress.BuildProgressDescriptor;
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
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.encoding.EncodingProjectManager;
import consulo.virtualFileSystem.util.VirtualFileUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class ExecutionHandler {
  private static final Logger LOG = Logger.getInstance(ExecutionHandler.class);

  private ExecutionHandler() {
  }

  /**
   * @param antBuildListener should not be null. Use {@link com.intellij.lang.ant.config.AntBuildListener#NULL}
   */
  public static void runBuild(AntBuildFileBase buildFile,
                              String[] targets,
                              DataContext dataContext,
                              List<BuildFileProperty> additionalProperties,
                              @Nonnull AntBuildListener antBuildListener) {
    FileDocumentManager.getInstance().saveAllDocuments();
    final AntCommandLineBuilder builder = new AntCommandLineBuilder();
    BuildViewManager buildViewManager = BuildViewManager.getInstance(buildFile.getProject());
    final GeneralCommandLine commandLine;
    BuildProgress<BuildProgressDescriptor> buildProgress;
    Project project = buildFile.getProject();

    try {
      builder.setBuildFile(buildFile.getAllOptions(), VirtualFileUtil.virtualToIoFile(buildFile.getVirtualFile()));
      builder.calculateProperties(dataContext, additionalProperties);
      builder.addTargets(targets);

      builder.getJavaParameters().setCharset(EncodingProjectManager.getInstance(buildFile.getProject()).getDefaultCharset());

      buildProgress = buildViewManager.createBuildProgress();

      commandLine = builder.getJavaParameters().toCommandLine();
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

    final boolean startInBackground = buildFile.isRunInBackground();

    new Task.Backgroundable(buildFile.getProject(), AntBundle.message("ant.build.progress.dialog.title"), true) {

      @Override
      public boolean shouldStartInBackground() {
        return startInBackground;
      }

      @Override
      public void run(@Nonnull final ProgressIndicator indicator) {
        try {
          runBuild(indicator, buildFile, antBuildListener, commandLine, buildProgress, targets);
        }
        catch (Throwable e) {
          LOG.error(e);
          antBuildListener.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
        }
      }
    }.queue();
  }

  private static void runBuild(@Nonnull ProgressIndicator progress,
                               @Nonnull final AntBuildFileBase buildFile,
                               @Nonnull final AntBuildListener antBuildListener,
                               @Nonnull GeneralCommandLine commandLine,
                               @Nonnull BuildProgress<BuildProgressDescriptor> buildProgress,
                               String[] targets) {
    final Project project = buildFile.getProject();

    String id = UUID.randomUUID().toString();

    String title = AntBundle.message("ant.build.local.history.label", buildFile.getName());
    DefaultBuildDescriptor buildDescriptor =
      new DefaultBuildDescriptor(id,
                                 buildFile.getName(),
                                 StringUtil.notNullize(buildFile.getVirtualFile().getParent().getPath()),
                                 System.currentTimeMillis());
    buildDescriptor.setActivateToolWindowWhenAdded(true);
    buildDescriptor.withRestartAction(new RunAction(buildFile, targets));
    buildProgress.start(new BuildProgressDescriptor() {
      @Nonnull
      @Override
      public String getTitle() {
        return buildFile.getName();
      }

      @Override
      @Nonnull
      public BuildDescriptor getBuildDescriptor() {
        return buildDescriptor;
      }
    });

    LocalHistory.getInstance().putSystemLabel(project, title);
    final AntProcessWrapper handler;
    try {
      handler = AntProcessWrapper.runCommandLine(commandLine);
    }
    catch (final ExecutionException e) {
      ApplicationManager.getApplication()
                        .invokeLater(() -> ExecutionErrorDialog.show(e,
                                                                     AntBundle.message("could.not.start.process.erorr.dialog.title"),
                                                                     project));

      buildProgress.fail(System.currentTimeMillis(), e.getMessage());
      antBuildListener.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
      return;
    }

    processRunningAnt(progress, handler, buildFile, antBuildListener, buildProgress);
    handler.waitFor();
  }

  private static void processRunningAnt(ProgressIndicator progress,
                                        AntProcessWrapper wrapper,
                                        AntBuildFile buildFile,
                                        AntBuildListener antBuildListener,
                                        @Nonnull BuildProgress<BuildProgressDescriptor> buildProgress) {
    final Project project = buildFile.getProject();
    final StatusBar statusbar = WindowManager.getInstance().getStatusBar(project);
    if (statusbar != null) {
      statusbar.setInfo(AntBundle.message("ant.build.started.status.message"));
    }

    final CheckCancelTask checkCancelTask = new CheckCancelTask(progress, wrapper.getProcessHandler());
    checkCancelTask.start(0);

    final OutputWatcher parser = OutputParser2.attachParser(project, wrapper, progress, buildFile, buildProgress);

    wrapper.addProcessListener(new ProcessListener() {
      @Override
      public void processTerminated(ProcessEvent event) {
        checkCancelTask.cancel();
        parser.setStopped(true);

        if (progress != null && progress.isCanceled()) {
          buildProgress.cancel();
          antBuildListener.buildFinished(AntBuildListener.ABORTED, 0);
        }
        else if (parser.getErrorsCount() > 0) {
          buildProgress.fail();
          antBuildListener.buildFinished(AntBuildListener.FINISHED_SUCCESSFULLY, parser.getErrorsCount());
        }
        else {
          buildProgress.finish();
          antBuildListener.buildFinished(AntBuildListener.FINISHED_SUCCESSFULLY, 0);
        }
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

    @Override
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
}
