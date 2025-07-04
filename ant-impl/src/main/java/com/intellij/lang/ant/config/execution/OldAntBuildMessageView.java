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
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.AntBuildListener;
import com.intellij.lang.ant.config.actions.ChangeViewAction;
import com.intellij.lang.ant.config.actions.PauseOutputAction;
import com.intellij.lang.ant.config.actions.StopAction;
import com.intellij.lang.ant.config.actions.VerboseAction;
import com.intellij.lang.ant.config.impl.AntBuildFileImpl;
import com.intellij.lang.ant.segments.OutputPacketProcessor;
import consulo.annotation.DeprecationInfo;
import consulo.apache.ant.execution.OutputWatcher;
import consulo.application.ApplicationManager;
import consulo.application.util.DateFormatUtil;
import consulo.application.util.function.Computable;
import consulo.dataContext.DataProvider;
import consulo.execution.test.Printable;
import consulo.execution.test.Printer;
import consulo.ide.impl.idea.ide.actions.NextOccurenceToolbarAction;
import consulo.ide.impl.idea.ide.actions.PreviousOccurenceToolbarAction;
import consulo.language.editor.PlatformDataKeys;
import consulo.language.editor.wolfAnalyzer.WolfTheProblemSolver;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.project.event.ProjectManagerListener;
import consulo.project.ui.wm.StatusBar;
import consulo.project.ui.wm.WindowManager;
import consulo.ui.ex.OccurenceNavigator;
import consulo.ui.ex.TreeExpander;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.IdeFocusTraversalPolicy;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.util.Alarm;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.content.ContentManager;
import consulo.ui.ex.content.event.ContentManagerAdapter;
import consulo.ui.ex.content.event.ContentManagerEvent;
import consulo.util.dataholder.Key;
import consulo.util.lang.Clock;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

@Deprecated
@DeprecationInfo("Not used anymore - replaced by new build view")
public final class OldAntBuildMessageView extends JPanel implements DataProvider, OccurenceNavigator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ant.execution.AntBuildMessageView");

  public enum MessageType {
    BUILD,
    TARGET,
    TASK,
    MESSAGE,
    ERROR,
  }

  private static final Key<OldAntBuildMessageView> KEY = Key.create("BuildMessageView.KEY");
  private static final String BUILD_CONTENT_NAME = AntBundle.message("ant.build.tab.content.title");

  public static final int PRIORITY_ERR = 0;
  public static final int PRIORITY_WARN = 1;
  public static final int PRIORITY_BRIEF = 2;
  public static final int PRIORITY_VERBOSE = 3;

  private OutputWatcher myParsingThread;
  private final Project myProject;
  private final JPanel myMessagePanel;
  private AntBuildFileBase myBuildFile;
  private final String[] myTargets;
  private int myPriorityThreshold = PRIORITY_BRIEF;
  private int myErrorCount;
  private int myWarningCount;
  private volatile boolean myIsOutputPaused = false;

  private AntOutputView myCurrentView;

  private final PlainTextView myPlainTextView;
  private final TreeView myTreeView;

  private final java.util.List<LogCommand> myLog = Collections.synchronizedList(new ArrayList<LogCommand>(1024));
  private volatile int myCommandsProcessedCount = 0;

  private JPanel myProgressPanel;

  private final AntMessageCustomizer[] myMessageCustomizers = AntMessageCustomizer.EP_NAME.getExtensions();

  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final Runnable myFlushLogRunnable = new Runnable() {
    @Override
    public void run() {
      if (myTreeView != null && myCommandsProcessedCount < myLog.size()) {
        if (!myIsOutputPaused) {
          new OutputFlusher().doFlush();
          myTreeView.scrollToLastMessage();
        }
      }
    }
  };

  private boolean myIsAborted;
  private ActionToolbar myLeftToolbar;
  private ActionToolbar myRightToolbar;
  private final TreeExpander myTreeExpander = new TreeExpander() {
    public boolean canCollapse() {
      return isTreeView();
    }

    public boolean canExpand() {
      return isTreeView();
    }

    public void collapseAll() {
      OldAntBuildMessageView.this.collapseAll();
    }

    public void expandAll() {
      OldAntBuildMessageView.this.expandAll();
    }
  };
  @NonNls
  public static final String FILE_PREFIX = "file:";

  private OldAntBuildMessageView(Project project, AntBuildFileBase buildFile, String[] targets) {
    super(new BorderLayout(2, 0));
    myProject = project;
    setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

    myPlainTextView = new PlainTextView(project);
    myTreeView = new TreeView(project, buildFile);

    myMessagePanel = new JPanel(new BorderLayout());
    myBuildFile = buildFile;
    myTargets = targets;

    showAntView(AntBuildFileImpl.TREE_VIEW.value(buildFile.getAllOptions()));
    setVerboseMode(AntBuildFileImpl.VERBOSE.value(buildFile.getAllOptions()));

    add(createToolbarPanel(), BorderLayout.WEST);
    add(myMessagePanel, BorderLayout.CENTER);
  }

  public void changeView() {
    showAntView(!isTreeView());
    if (myBuildFile != null) {
      myBuildFile.setTreeView(isTreeView());
    }
  }

  private boolean isTreeView() {
    return myCurrentView == myTreeView;
  }

  public void setVerboseMode(boolean verbose) {
    changeDetalizationLevel(verbose ? PRIORITY_VERBOSE : PRIORITY_BRIEF);
    if (myBuildFile != null) {
      myBuildFile.setVerboseMode(verbose);
    }
  }

  public boolean isVerboseMode() {
    return myPriorityThreshold == PRIORITY_VERBOSE;
  }

  private synchronized void changeDetalizationLevel(int priorityThreshold) {
    myPriorityThreshold = priorityThreshold;

    TreeView.TreeSelection selection = myTreeView.getSelection();
    myTreeView.clearAllMessages();
    myPlainTextView.clearAllMessages();
    myTreeView.setActionsEnabled(false);

    new OutputFlusher() {
      public void doFlush() {
        final int processedCount = myCommandsProcessedCount;
        for (int i = 0; i < processedCount; i++) {
          LogCommand command = myLog.get(i);
          proceedOneCommand(command);
        }
        flushDelayedMessages();
      }
    }.doFlush();
    myTreeView.setActionsEnabled(true);
    if (!myTreeView.restoreSelection(selection)) {
      myTreeView.scrollToLastMessage();
    }
  }

  private void showAntView(boolean treeView) {
    AntOutputView oldView = getOutputView(treeView);
    AntOutputView newView = getOutputView(!treeView);
    myCurrentView = newView;
    myMessagePanel.remove(oldView.getComponent());
    myMessagePanel.add(newView.getComponent(), BorderLayout.CENTER);
    myMessagePanel.validate();

    JComponent component = IdeFocusTraversalPolicy.getPreferredFocusedComponent(myMessagePanel);
    component.requestFocus();
    repaint();
  }

  private AntOutputView getOutputView(boolean isText) {
    return isText ? myPlainTextView : myTreeView;
  }

  public AntBuildFileBase getBuildFile() {
    return myBuildFile;
  }

  public void setParsingThread(OutputWatcher parsingThread) {
    myParsingThread = parsingThread;
    myIsAborted = false;
  }

  public void stopProcess() {
    if (myParsingThread != null) {
      myParsingThread.stopProcess();
    }
    myIsAborted = true;
    myLeftToolbar.updateActionsImmediately();
    myRightToolbar.updateActionsImmediately();
  }

  public boolean isStopped() {
    return myParsingThread == null || myParsingThread.isStopped();
  }

  public boolean isStoppedOrTerminateRequested() {
    return myParsingThread == null || myParsingThread.isTerminateInvoked() || isStopped();
  }

  private void close() {
    consulo.project.ui.view.MessageView messageView = consulo.project.ui.view.MessageView.getInstance(myProject);
    Content[] contents = messageView.getContentManager().getContents();
    for (Content content : contents) {
      if (content.getComponent() == this) {
        messageView.getContentManager().removeContent(content, true);
        return;
      }
    }
  }

  private JPanel createToolbarPanel() {
//    RunAction runAction = new RunAction(this);
//    runAction.registerCustomShortcutSet(CommonShortcuts.getRerun(), this);

    DefaultActionGroup leftActionGroup = new DefaultActionGroup();
//    leftActionGroup.add(runAction);
    leftActionGroup.add(new PauseOutputAction(this));
    leftActionGroup.add(new StopAction(this));
    leftActionGroup.add(new PreviousOccurenceToolbarAction(this));
    leftActionGroup.add(new NextOccurenceToolbarAction(this));

    DefaultActionGroup rightActionGroup = new DefaultActionGroup();
    rightActionGroup.add(new ChangeViewAction(this));
    rightActionGroup.add(new VerboseAction(this));
    rightActionGroup.add(CommonActionsManager.getInstance().createExpandAllAction(myTreeExpander, this));
    rightActionGroup.add(CommonActionsManager.getInstance().createCollapseAllAction(myTreeExpander, this));
    rightActionGroup.add(myTreeView.createToggleAutoscrollAction());

    myLeftToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.ANT_MESSAGES_TOOLBAR, leftActionGroup, false);
    myLeftToolbar.setTargetComponent(this);
    JPanel toolbarPanel = new JPanel(new GridLayout(1, 2, 2, 0));
    toolbarPanel.add(myLeftToolbar.getComponent());
    myRightToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.ANT_MESSAGES_TOOLBAR, rightActionGroup, false);
    myRightToolbar.setTargetComponent(this);
    toolbarPanel.add(myRightToolbar.getComponent());

    return toolbarPanel;
  }

  private synchronized void addCommand(LogCommand command) {
    if (!myAlarm.isDisposed()) {
      myLog.add(command);
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(myFlushLogRunnable, 100L);
    }
  }

  public void startBuild(String buildName) {
    addCommand(new StartBuildCommand(buildName));
  }

  public void buildFailed(String buildName) {
    addCommand(new BuildFailedCommand(buildName));
  }

  public void startTarget(String targetName) {
    addCommand(new StartTargetCommand(targetName));
  }

  public void startTask(String taskName) {
    addCommand(new StartTaskCommand(taskName));
  }

  public void outputMessage(final String text, final int priority) {
    final AntMessage customizedMessage = getCustomizedMessage(text, priority);
    final AntMessage message = customizedMessage != null
      ? customizedMessage
      : new AntMessage(MessageType.MESSAGE, priority, text, null, 0, 0);
    updateErrorAndWarningCounters(message.getPriority());
    addCommand(new AddMessageCommand(message));
  }

  @Nullable
  private AntMessage getCustomizedMessage(final String text, final int priority) {
    AntMessage customizedMessage = null;

    for (AntMessageCustomizer customizer : myMessageCustomizers) {
      customizedMessage = customizer.createCustomizedMessage(text, priority);
      if (customizedMessage != null) {
        break;
      }
    }

    return customizedMessage;
  }

  public void outputError(String error, int priority) {
    //updateErrorAndWarningCounters(priority);
    AntMessage message = createErrorMessage(MessageType.ERROR, priority, error);
    addMessage(MessageType.ERROR, priority, error, message.getFile(), message.getLine(), message.getColumn());
    WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(myProject);
    wolf.queue(message.getFile());
  }

  public void outputException(String exception) {
    updateErrorAndWarningCounters(PRIORITY_ERR);
    AntMessage message = createErrorMessage(MessageType.ERROR, 0, exception);
    addCommand(new AddExceptionCommand(message));
    WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(myProject);
    wolf.queue(message.getFile());
  }


  private void updateErrorAndWarningCounters(int priority) {
    if (priority == PRIORITY_ERR) {
      myErrorCount++;
    }
    else if (priority == PRIORITY_WARN) {
      myWarningCount++;
    }
  }

  public void finishTarget() {
    addCommand(new FinishTargetCommand());
  }

  public void finishTask() {
    addCommand(new FinishTaskCommand());
  }

  @Override
  public Object getData(Key<?> dataId) {
    Object data = myCurrentView.getData(dataId);
    if (data != null) return data;
    if (PlatformDataKeys.TREE_EXPANDER == dataId) {
      return myTreeExpander;
    }
    return null;
  }

  private static AntMessage createErrorMessage(MessageType type, int priority, String text) {
    if (text.startsWith(FILE_PREFIX)) {
      text = text.substring(FILE_PREFIX.length());
    }

    int afterLineNumberIndex = text.indexOf(": "); // end of file_name_and_line_number sequence
    if (afterLineNumberIndex != -1) {
      String fileAndLineNumber = text.substring(0, afterLineNumberIndex);
      int index = fileAndLineNumber.lastIndexOf(':');
      if (index != -1) {
        String fileName = fileAndLineNumber.substring(0, index);
        String lineNumberStr = fileAndLineNumber.substring(index + 1, fileAndLineNumber.length()).trim();
        try {
          int line = Integer.parseInt(lineNumberStr);

          final File file = new File(fileName);
          final VirtualFile result = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
            public VirtualFile compute() {
              String url =
                VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, file.getAbsolutePath().replace(File.separatorChar, '/'));
              return VirtualFileManager.getInstance().findFileByUrl(url);
            }
          });

          // convert separators
          text = fileName.replace('/', File.separatorChar) + ':' + line + text.substring(afterLineNumberIndex);

          return new AntMessage(type, priority, text, result, line, 1);
        }
        catch (NumberFormatException ignored) {
        }
      }
    }

    return new AntMessage(type, priority, text, null, 0, 0);
  }

  private void addMessage(MessageType type, int priority, String text, VirtualFile file, int line, int column) {
    AntMessage message = new AntMessage(type, priority, text, file, line, column);
    addCommand(new AddMessageCommand(message));
  }

  public void outputJavacMessage(MessageType type, String[] text, VirtualFile file, String url, int line, int column) {
    int priority = type == MessageType.ERROR ? PRIORITY_ERR : PRIORITY_VERBOSE;
    updateErrorAndWarningCounters(priority);
    AntMessage message = new AntMessage(type, priority, text, file, line, column);
    addCommand(new AddJavacMessageCommand(message, url));
    if (type == MessageType.ERROR) {
      WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(myProject);
      wolf.queue(file);
    }
  }

  private JComponent getComponent() {
    return this;
  }

  public void emptyAll() {
    myLog.clear();
    myCommandsProcessedCount = 0;
    myErrorCount = 0;
    myWarningCount = 0;
    myPlainTextView.clearAllMessages();
    myTreeView.clearAllMessages();
  }

  private void collapseAll() {
    myTreeView.collapseAll();
  }

  private void expandAll() {
    myTreeView.expandAll();
  }

  private static final class CloseListener extends ContentManagerAdapter implements ProjectManagerListener {
    private Content myContent;
    private boolean myCloseAllowed = false;
    private final ContentManager myContentManager;
    private final Project myProject;

    private CloseListener(Content content, ContentManager contentManager, Project project) {
      myContent = content;
      myContentManager = contentManager;
      myProject = project;
      contentManager.addContentManagerListener(this);
      ProjectManager.getInstance().addProjectManagerListener(myProject, this);
    }

    public void contentRemoved(ContentManagerEvent event) {
      if (event.getContent() == myContent) {
        myContentManager.removeContentManagerListener(this);
        OldAntBuildMessageView buildMessageView = myContent.getUserData(KEY);
        if (!myCloseAllowed) {
          buildMessageView.stopProcess();
        }
        ProjectManager.getInstance().removeProjectManagerListener(myProject, this);
        myContent.release();
        myContent = null;
        buildMessageView.myBuildFile = null;
        buildMessageView.myPlainTextView.dispose();
      }
    }

    public void contentRemoveQuery(ContentManagerEvent event) {
      if (event.getContent() == myContent) {
        boolean canClose = closeQuery();
        if (!canClose) {
          event.consume();
        }
      }
    }

    public void projectOpened(Project project) {
    }

    public void projectClosed(Project project) {
      if (myContent != null) {
        myContentManager.removeContent(myContent, true);
      }
    }

    public void projectClosing(Project project) {
    }

    public boolean canCloseProject(Project project) {
      return closeQuery();
    }

    /**
     * @return true if content can be closed
     */
    private boolean closeQuery() {
      if (myContent == null) {
        return true;
      }

      OldAntBuildMessageView messageView = myContent.getUserData(KEY);

      if (messageView.isStoppedOrTerminateRequested()) {
        return true;
      }

      if (myCloseAllowed) return true;

      int result = Messages.showYesNoCancelDialog(AntBundle.message("ant.process.is.active.terminate.confirmation.text"),
                                                  AntBundle.message("close.ant.build.messages.dialog.title"), Messages.getQuestionIcon());
      if (result == 0) { // yes
        messageView.stopProcess();
        myCloseAllowed = true;
        return true;
      }

      if (result == 1) { // no
        // close content and leave the process running
        myCloseAllowed = true;
        return true;
      }

      return false;
    }

  }

  private abstract static class LogCommand {
    private final int myPriority;

    LogCommand(int priority) {
      myPriority = priority;
    }

    final int getPriority() {
      return myPriority;
    }

    abstract void execute(AntOutputView outputView);
  }

  private static final class StartBuildCommand extends LogCommand {
    private final AntMessage myMessage;

    StartBuildCommand(String buildName) {
      super(0);
      myMessage = new AntMessage(MessageType.BUILD, 0, buildName, null, 0, 0);
    }

    void execute(AntOutputView outputView) {
      outputView.startBuild(myMessage);
    }
  }

  private static final class BuildFailedCommand extends LogCommand {
    private final AntMessage myMessage;

    BuildFailedCommand(String buildName) {
      super(0);
      myMessage = new AntMessage(MessageType.ERROR, 0, AntBundle.message("cannot.start.build.name.error.message", buildName), null, 0, 0);
    }

    void execute(AntOutputView outputView) {
      outputView.buildFailed(myMessage);
    }
  }

  private static final class FinishBuildCommand extends LogCommand {
    private final String myFinishStatusText;

    FinishBuildCommand(String finishStatusText) {
      super(0);
      myFinishStatusText = finishStatusText;
    }

    void execute(AntOutputView outputView) {
      outputView.finishBuild(myFinishStatusText);
    }
  }

  private static final class StartTargetCommand extends LogCommand {
    private final AntMessage myMessage;

    StartTargetCommand(String targetName) {
      super(0);
      myMessage = new AntMessage(MessageType.TARGET, 0, targetName, null, 0, 0);
    }

    void execute(AntOutputView outputView) {
      outputView.startTarget(myMessage);
    }
  }

  private static final class FinishTargetCommand extends LogCommand {
    FinishTargetCommand() {
      super(0);
    }

    void execute(AntOutputView outputView) {
      outputView.finishTarget();
    }
  }


  private static final class StartTaskCommand extends LogCommand {
    private final AntMessage myMessage;

    StartTaskCommand(String taskName) {
      super(0);
      myMessage = new AntMessage(MessageType.TASK, 0, taskName, null, 0, 0);
    }

    void execute(AntOutputView outputView) {
      outputView.startTask(myMessage);
    }
  }

  private static final class FinishTaskCommand extends LogCommand {
    FinishTaskCommand() {
      super(0);
    }

    public void execute(AntOutputView outputView) {
      outputView.finishTask();
    }
  }

  private static final class AddMessageCommand extends LogCommand {
    final AntMessage myAntMessage;

    AddMessageCommand(AntMessage antMessage) {
      super(antMessage.getPriority());
      myAntMessage = antMessage;
    }

    void execute(AntOutputView outputView) {
      outputView.addMessage(myAntMessage);
    }
  }

  private final class AddExceptionCommand extends LogCommand {
    private final AntMessage myAntMessage;

    AddExceptionCommand(AntMessage antMessage) {
      super(antMessage.getPriority());
      myAntMessage = antMessage;
    }

    void execute(AntOutputView outputView) {
      outputView.addException(myAntMessage, isVerboseMode());
    }
  }

  private static final class AddJavacMessageCommand extends LogCommand {
    private final String myUrl;
    private final AntMessage myAntMessage;

    AddJavacMessageCommand(AntMessage antMessage, String url) {
      super(antMessage.getPriority());
      myAntMessage = antMessage;
      myUrl = url;
    }

    void execute(AntOutputView outputView) {
      outputView.addJavacMessage(myAntMessage, myUrl);
    }
  }

  public String[] getTargets() {
    return myTargets;
  }

  private int getErrorCount() {
    return myErrorCount;
  }

  private int getWarningCount() {
    return myWarningCount;
  }

  void buildFinished(boolean isProgressAborted,
                     long buildTimeInMilliseconds,
                     @Nonnull final AntBuildListener antBuildListener,
                     OutputPacketProcessor dispatcher) {
    final boolean aborted = isProgressAborted || myIsAborted;
    final String message = getFinishStatusText(aborted, buildTimeInMilliseconds);

    dispatcher.processOutput(new Printable() {
      @Override
      public void printOn(Printer printer) {
        if (!myProject.isDisposed()) { // if not disposed
          addCommand(new FinishBuildCommand(message));
        }
      }
    });
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (!myIsOutputPaused) {
          new OutputFlusher().doFlush();
        }
        final AntBuildFileBase buildFile = myBuildFile;
        if (buildFile != null) {
          if (getErrorCount() == 0 && buildFile.isViewClosedWhenNoErrors()) {
            close();
          }
          else if (getErrorCount() > 0) {
            myTreeView.scrollToFirstError();
          }
          else {
            myTreeView.scrollToStatus();
          }
        }
        else {
          myTreeView.scrollToLastMessage();
        }
        VirtualFileManager.getInstance().asyncRefresh(new Runnable() {
          public void run() {
            antBuildListener.buildFinished(aborted ? AntBuildListener.ABORTED : AntBuildListener.FINISHED_SUCCESSFULLY, getErrorCount());
          }
        });
      }
    });
  }

  public String getFinishStatusText(boolean isAborted, long buildTimeInMilliseconds) {
    int errors = getErrorCount();
    int warnings = getWarningCount();
    final String theDateAsString = DateFormatUtil.formatDateTime(Clock.getTime());

    String formattedBuildTime = formatBuildTime(buildTimeInMilliseconds / 1000);

    if (isAborted) {
      return AntBundle.message("build.finished.status.ant.build.aborted", formattedBuildTime, theDateAsString);
    }
    else if (errors == 0 && warnings == 0) {
      return AntBundle.message("build.finished.status.ant.build.completed.successfully", formattedBuildTime, theDateAsString);
    }
    else if (errors == 0) {
      return AntBundle.message("build.finished.status.ant.build.completed.with.warnings", warnings, formattedBuildTime, theDateAsString);
    }
    else {
      return AntBundle
        .message("build.finished.status.ant.build.completed.with.errors.warnings", errors, warnings, formattedBuildTime, theDateAsString);
    }
  }

  private static String formatBuildTime(long seconds) {
    if (seconds == 0) {
      return "0s";
    }
    final StringBuilder sb = new StringBuilder();
    if (seconds >= 3600) {
      sb.append(seconds / 3600).append("h ");
      seconds %= 3600;
    }
    if (seconds >= 60 || sb.length() > 0) {
      sb.append(seconds / 60).append("m ");
      seconds %= 60;
    }
    if (seconds > 0 || sb.length() > 0) {
      sb.append(seconds).append("s");
    }
    return sb.toString();
  }

  public boolean isOutputPaused() {
    return myIsOutputPaused;
  }

  public synchronized void setOutputPaused(boolean outputPaused) {
    if (outputPaused == myIsOutputPaused) return;
    if (myIsOutputPaused) {
      new OutputFlusher().doFlush();
    }
    myIsOutputPaused = outputPaused;
  }

  private class OutputFlusher {
    private final ArrayList<AntMessage> myDelayedMessages = new ArrayList<AntMessage>();

    public void doFlush() {
      int currentProcessedCount = myCommandsProcessedCount;
      while (currentProcessedCount < myLog.size()) {
        final LogCommand command = myLog.get(currentProcessedCount++);
        proceedOneCommand(command);
      }
      myCommandsProcessedCount = currentProcessedCount;
      flushDelayedMessages();
    }

    protected final void proceedOneCommand(LogCommand command) {
      if (command.getPriority() > myPriorityThreshold) return;
      // proceed messages in a special way
      if (command instanceof AddMessageCommand) {
        AddMessageCommand addMessageCommand = (AddMessageCommand)command;
        myDelayedMessages.add(addMessageCommand.myAntMessage);
      }
      else {
        flushDelayedMessages(); // message type changed -> flush
        command.execute(myTreeView);
        command.execute(myPlainTextView);
      }
    }

    protected final void flushDelayedMessages() {
      if (!myDelayedMessages.isEmpty()) {
        AntMessage[] messages = myDelayedMessages.toArray(new AntMessage[myDelayedMessages.size()]);
        myDelayedMessages.clear();
        myTreeView.addMessages(messages);
        myPlainTextView.addMessages(messages);
      }
    }
  }

  public String getNextOccurenceActionName() {
    return myTreeView.getNextOccurenceActionName();
  }

  public String getPreviousOccurenceActionName() {
    return myTreeView.getPreviousOccurenceActionName();
  }

  public OccurenceInfo goNextOccurence() {
    return isTreeView() ? myTreeView.goNextOccurence() : null;
  }

  public OccurenceInfo goPreviousOccurence() {
    return isTreeView() ? myTreeView.goPreviousOccurence() : null;
  }

  public boolean hasNextOccurence() {
    return isTreeView() && myTreeView.hasNextOccurence();
  }

  public boolean hasPreviousOccurence() {
    return isTreeView() && myTreeView.hasPreviousOccurence();
  }

  @Deprecated
  public void setBuildCommandLine(String commandLine) {
    myPlainTextView.setBuildCommandLine(commandLine);

  }
}
