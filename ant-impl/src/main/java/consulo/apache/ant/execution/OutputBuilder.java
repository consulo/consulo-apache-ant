package consulo.apache.ant.execution;

import com.intellij.java.compiler.impl.javaCompiler.FileObject;
import com.intellij.java.compiler.impl.javaCompiler.javac.JavacOutputParser;
import consulo.application.ApplicationManager;
import consulo.build.ui.FilePosition;
import consulo.build.ui.event.MessageEvent;
import consulo.build.ui.progress.BuildProgress;
import consulo.build.ui.progress.BuildProgressDescriptor;
import consulo.compiler.CompilerMessageCategory;
import consulo.logging.Logger;
import consulo.process.ProcessHandler;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import jetbrains.buildServer.messages.serviceMessages.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author VISTALL
 * @since 08/05/2023
 */
public class OutputBuilder implements OutputWatcher, MessageProcessor {
  private static final String JAVAC = "javac";
  private static final String ECHO = "echo";

  private static final Logger LOG = Logger.getInstance(OutputBuilder.class);
  private final Project myProject;
  private final BuildProgress<BuildProgressDescriptor> myBuildProgress;
  private final ProcessHandler myProcessHandler;
  private boolean isStopped;
  private List<String> myJavacMessages;
  private boolean myIsEcho;
  private int myErrorsCount;

  private Map<String, BuildProgress<BuildProgressDescriptor>> myTargets = new ConcurrentHashMap<>();
  private Map<String, BuildProgress<BuildProgressDescriptor>> myTasks = new ConcurrentHashMap<>();

  private Deque<BuildProgress<BuildProgressDescriptor>> myQueue = new ConcurrentLinkedDeque<>();

  public OutputBuilder(Project project,
                       ProcessHandler processHandler,
                       BuildProgress<BuildProgressDescriptor> buildProgress) {
    myProject = project;
    myProcessHandler = processHandler;
    myBuildProgress = buildProgress;

    myQueue.add(myBuildProgress);
  }

  @Override
  public int getErrorsCount() {
    return myErrorsCount;
  }

  @Override
  public final void stopProcess() {
    myProcessHandler.destroyProcess();
  }

  @Override
  public boolean isTerminateInvoked() {
    return myProcessHandler.isProcessTerminating();
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
  public void onMessage(@Nullable ServiceMessage serviceMessage, @Nonnull String text) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(text);
    }

    BuildProgress<BuildProgressDescriptor> current = myQueue.getLast();

    if (serviceMessage == null) {
      current.output(text + "\n", true);
      return;
    }

    if (serviceMessage instanceof BuildStatus buildStatus) {
      boolean started = "buildStarted".equals(buildStatus.getStatus());

      if (started) {
        // we already started it
      }
      else {
        if (myErrorsCount > 0) {
          myBuildProgress.fail();
        }
        else {
          myBuildProgress.finish();
        }
      }
    }
    else if (serviceMessage instanceof ProgressStart progressStart) {
      BuildProgress<BuildProgressDescriptor> childProgress = current.startChildProgress("target: " + progressStart.getArgument());
      myTargets.put(progressStart.getArgument(), childProgress);
      myQueue.addLast(childProgress);
    }
    else if (serviceMessage instanceof TestStarted started) {
      String taskName = started.getTestName();
      BuildProgress<BuildProgressDescriptor> childProgress = current.startChildProgress("task: " + taskName);
      myTasks.put(taskName, childProgress);
      myQueue.addLast(childProgress);

      if (JAVAC.equals(taskName)) {
        myJavacMessages = new ArrayList<>();
      }
    }
    else if (serviceMessage instanceof TestFinished testFinished) {
      String taskName = testFinished.getTestName();
      BuildProgress<BuildProgressDescriptor> childProgress = myTasks.remove(taskName);
      final List<String> javacMessages = myJavacMessages;
      myJavacMessages = null;
      int currentErrors = processJavacMessages(javacMessages, myQueue.getLast(), myProject);
      myErrorsCount += currentErrors;
      myIsEcho = false;

      if (childProgress != null) {
        if (currentErrors > 0) {
          childProgress.fail();
        }
        else {
          childProgress.finish();
        }

        myQueue.remove(childProgress);
      }
    }
    else if (serviceMessage instanceof ProgressFinish progressFinish) {
      BuildProgress<BuildProgressDescriptor> childProgress = myTargets.remove(progressFinish.getArgument());
      final List<String> javacMessages = myJavacMessages;
      myJavacMessages = null;
      int currentErrors = processJavacMessages(javacMessages, myQueue.getLast(), myProject);
      myErrorsCount += currentErrors;
      myIsEcho = false;

      if (childProgress != null) {
        if (currentErrors > 0) {
          childProgress.fail();
        }
        else {
          childProgress.finish();
        }

        myQueue.remove(childProgress);
      }
    }
    else if (serviceMessage instanceof Message messageObj) {
      String messageText = messageObj.getText();
      // org.apache.tools.ant.Project.MSG_ERR = 0
      boolean isError = "0".equals(messageObj.getStatus());
      boolean isWarn = "1".equals(messageObj.getStatus());
      boolean isInfo = "2".equals(messageObj.getStatus());

      if (isError || isWarn || isInfo) {
        if (myJavacMessages != null) {
          myJavacMessages.add(messageText);
        }
        else {
          myQueue.getLast().output(messageText + "\n", !isError);

          if (isError) {
            myErrorsCount++;
          }
        }
      }
    }

//    if (AntLoggerConstants.TARGET == tagName) {
//      setProgressStatistics(AntBundle.message("target.tag.name.status.text", tagValue));
//    }
//    else if (AntLoggerConstants.TASK == tagName) {
//      setProgressText(AntBundle.message("executing.task.tag.value.status.text", tagValue));
//      if (JAVAC.equals(tagValue)) {
//        myJavacMessages = new ArrayList<String>();
//      }
//      else if (ECHO.equals(tagValue)) {
//        myIsEcho = true;
//      }
//    }
//
//    if (myJavacMessages != null && (AntLoggerConstants.MESSAGE == tagName || AntLoggerConstants.ERROR == tagName)) {
//      myJavacMessages.add(tagValue);
//      return;
//    }
//
//    if (AntLoggerConstants.MESSAGE == tagName) {
//      if (myIsEcho) {
//        myMessageView.outputMessage(tagValue, AntBuildMessageView.PRIORITY_VERBOSE);
//      }
//      else {
//        myMessageView.outputMessage(tagValue, priority);
//      }
//    }
//    else if (AntLoggerConstants.TARGET == tagName) {
//      myMessageView.startTarget(tagValue);
//    }
//    else if (AntLoggerConstants.TASK == tagName) {
//      myMessageView.startTask(tagValue);
//    }
//    else if (AntLoggerConstants.ERROR == tagName) {
//      myMessageView.outputError(tagValue, priority);
//    }
//    else if (AntLoggerConstants.EXCEPTION == tagName) {
//      String exceptionText = tagValue.replace(AntLoggerConstants.EXCEPTION_LINE_SEPARATOR, '\n');
//      myMessageView.outputException(exceptionText);
//    }
//    else if (AntLoggerConstants.BUILD == tagName) {
//      myMessageView.startBuild(myBuildName);
//    }
//    else if (AntLoggerConstants.TARGET_END == tagName || AntLoggerConstants.TASK_END == tagName) {
//      final List<String> javacMessages = myJavacMessages;
//      myJavacMessages = null;
//      processJavacMessages(javacMessages, myMessageView, myProject);
//      myIsEcho = false;
//      if (AntLoggerConstants.TARGET_END == tagName) {
//        myMessageView.finishTarget();
//      }
//      else {
//        myMessageView.finishTask();
//      }
//    }
  }

  private static int processJavacMessages(List<String> javacMessages,
                                          BuildProgress<BuildProgressDescriptor> buildProgress,
                                          Project project) {
    if (javacMessages == null) return 0;

    com.intellij.java.compiler.impl.OutputParser outputParser = new JavacOutputParser(project);


    AtomicInteger errorCount = new AtomicInteger();
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
        if (category == CompilerMessageCategory.ERROR) {
          errorCount.incrementAndGet();
        }
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            VirtualFile file = url == null ? null : VirtualFileManager.getInstance().findFileByUrl(url);
            if (file != null) {
              buildProgress.fileMessage(message,
                                        message,
                                        convertCategory(category),
                                        new FilePosition(new File(file.getCanonicalPath()),
                                                         lineNum - 1,
                                                         columnNum,
                                                         lineNum - 1,
                                                         columnNum));
            }
            else {
              buildProgress.message(message,
                                    message,
                                    convertCategory(category),
                                    null);
            }

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

    return errorCount.get();
  }

  private static MessageEvent.Kind convertCategory(CompilerMessageCategory category) {
    if (CompilerMessageCategory.ERROR.equals(category)) {
      return MessageEvent.Kind.ERROR;
    }
    if (CompilerMessageCategory.WARNING.equals(category)) {
      return MessageEvent.Kind.WARNING;
    }
    return MessageEvent.Kind.INFO;
  }
}
