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
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.actions.TargetAction;
import com.intellij.lang.ant.dom.AntDomFileDescription;
import consulo.annotation.component.ServiceImpl;
import consulo.apache.ant.util.AntJavaSdkUtil;
import consulo.application.AccessRule;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.application.util.Semaphore;
import consulo.component.ComponentManager;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.component.util.ModificationTracker;
import consulo.component.util.config.AbstractProperty;
import consulo.component.util.config.ValueProperty;
import consulo.content.bundle.Sdk;
import consulo.dataContext.DataContext;
import consulo.execution.RunManager;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.ConfigurationType;
import consulo.execution.configuration.ConfigurationTypeUtil;
import consulo.execution.configuration.RunConfiguration;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.editor.PlatformDataKeys;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.startup.StartupManager;
import consulo.proxy.EventDispatcher;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.event.VirtualFileAdapter;
import consulo.virtualFileSystem.event.VirtualFileEvent;
import consulo.xml.psi.xml.XmlFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.util.*;

@State(name = "AntConfiguration", storages = @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/ant.xml"))
@ServiceImpl
@Singleton
public class AntConfigurationImpl extends AntConfigurationBase implements PersistentStateComponent<Element>, ModificationTracker {
  public static final ValueProperty<AntReference> DEFAULT_ANT = new ValueProperty<AntReference>("defaultAnt", AntReference.BUNDLED_ANT);
  public static final ValueProperty<AntConfiguration> INSTANCE = new ValueProperty<AntConfiguration>("$instance", null);
  public static final AbstractProperty<String> DEFAULT_JDK_NAME = new AbstractProperty<String>() {
    public String getName() {
      return "$defaultJDKName";
    }

    @Nullable
    public String getDefault(final AbstractPropertyContainer container) {
      return get(container);
    }

    @Nullable
    public String get(final AbstractPropertyContainer container) {
      if (!container.hasProperty(this)) {
        return null;
      }
      return AntJavaSdkUtil.getBundleSdkName();
    }

    public String copy(final String jdkName) {
      return jdkName;
    }
  };

  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.config.impl.AntConfigurationImpl");
  @NonNls
  private static final String BUILD_FILE = "buildFile";
  @NonNls
  private static final String CONTEXT_MAPPING = "contextMapping";
  @NonNls
  private static final String CONTEXT = "context";
  @NonNls
  private static final String URL = "url";
  @NonNls
  private static final String EXECUTE_ON_ELEMENT = "executeOn";
  @NonNls
  private static final String EVENT_ELEMENT = "event";
  @NonNls
  private static final String TARGET_ELEMENT = "target";

  private final consulo.language.psi.PsiManager myPsiManager;
  private final Map<ExecutionEvent, Pair<AntBuildFile, String>> myEventToTargetMap =
    new HashMap<ExecutionEvent, Pair<AntBuildFile, String>>();
  private final List<AntBuildFileBase> myBuildFiles = new ArrayList<AntBuildFileBase>();
  private volatile AntBuildFileBase[] myBuildFilesArray = null; // cached result of call to myBuildFiles.toArray()
  private final Map<AntBuildFile, AntBuildModelBase> myModelToBuildFileMap = new HashMap<AntBuildFile, AntBuildModelBase>();
  private final Map<VirtualFile, consulo.virtualFileSystem.VirtualFile> myAntFileToContextFileMap =
    new java.util.HashMap<VirtualFile, consulo.virtualFileSystem.VirtualFile>();
  private final EventDispatcher<AntConfigurationListener> myEventDispatcher = EventDispatcher.create(AntConfigurationListener.class);
  private final AntWorkspaceConfiguration myAntWorkspaceConfiguration;
  private final StartupManager myStartupManager;
  private boolean myInitializing;
  private volatile long myModificationCount = 0;

  @Inject
  public AntConfigurationImpl(final Project project,
                              final AntWorkspaceConfiguration antWorkspaceConfiguration,
                              final DaemonCodeAnalyzer daemon) {
    super(project);
    getProperties().registerProperty(DEFAULT_ANT, AntReference.EXTERNALIZER);
    getProperties().rememberKey(INSTANCE);
    getProperties().rememberKey(DEFAULT_JDK_NAME);
    INSTANCE.set(getProperties(), this);
    myAntWorkspaceConfiguration = antWorkspaceConfiguration;
    myPsiManager = PsiManager.getInstance(project);
    myStartupManager = StartupManager.getInstance(project);
    addAntConfigurationListener(new AntConfigurationListener() {
      public void configurationLoaded() {
        restartDaemon();
      }

      public void buildFileChanged(final AntBuildFile buildFile) {
        restartDaemon();
      }

      public void buildFileAdded(final AntBuildFile buildFile) {
        restartDaemon();
      }

      public void buildFileRemoved(final AntBuildFile buildFile) {
        restartDaemon();
      }

      private void restartDaemon() {
        if (ApplicationManager.getApplication().isDispatchThread()) {
          daemon.restart();
        }
        else {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              daemon.restart();
            }
          });
        }
      }
    });
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileAdapter() {
      public void beforeFileDeletion(final VirtualFileEvent event) {
        final consulo.virtualFileSystem.VirtualFile vFile = event.getFile();
        // cleanup
        for (AntBuildFile file : getBuildFiles()) {
          if (vFile.equals(file.getVirtualFile())) {
            removeBuildFile(file);
            break;
          }
        }
        for (Iterator<Map.Entry<VirtualFile, consulo.virtualFileSystem.VirtualFile>> it =
             myAntFileToContextFileMap.entrySet().iterator(); it.hasNext(); ) {
          final Map.Entry<consulo.virtualFileSystem.VirtualFile, consulo.virtualFileSystem.VirtualFile> entry = it.next();
          if (vFile.equals(entry.getKey()) || vFile.equals(entry.getValue())) {
            it.remove();
          }
        }
      }
    }, project);
  }


  public Element getState() {
    try {
      final Element e = new Element("state");
      writeExternal(e);
      return e;
    }
    catch (WriteExternalException e1) {
      LOG.error(e1);
      return null;
    }
  }

  public void loadState(Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  private volatile Boolean myIsInitialized = null;

  public boolean isInitialized() {
    final Boolean initialized = myIsInitialized;
    return initialized == null || initialized.booleanValue();
  }

  public AntBuildFileBase[] getBuildFiles() {
    AntBuildFileBase[] result = myBuildFilesArray;
    if (result == null) {
      synchronized (myBuildFiles) {
        result = myBuildFilesArray;
        if (result == null) {
          myBuildFilesArray = result = myBuildFiles.toArray(new AntBuildFileBase[myBuildFiles.size()]);
        }
      }
    }
    return result;
  }

  public AntBuildFile addBuildFile(final VirtualFile file) throws AntNoFileException {
    final AntBuildFile[] result = new AntBuildFile[]{null};
    final AntNoFileException[] ex = new AntNoFileException[]{null};
    final String title = AntBundle.message("register.ant.build.progress", file.getPresentableUrl());
    ProgressManager.getInstance().run(new Task.Modal(getProject(), title, false) {
      @Nullable
      public NotificationInfo getNotificationInfo() {
        return new NotificationInfo("Ant", "Ant Task Finished", "");
      }

      public void run(@Nonnull final ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        indicator.pushState();
        try {
          indicator.setText(title);
          myModificationCount++;
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              try {
                result[0] = addBuildFileImpl(file);
                updateRegisteredActions();
              }
              catch (AntNoFileException e) {
                ex[0] = e;
              }
            }
          });
          if (result[0] != null) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                myEventDispatcher.getMulticaster().buildFileAdded(result[0]);
              }
            });
          }
        }
        finally {
          indicator.popState();
        }
      }
    });
    if (ex[0] != null) {
      throw ex[0];
    }
    return result[0];
  }

  public void removeBuildFile(final AntBuildFile file) {
    myModificationCount++;
    removeBuildFileImpl(file);
    updateRegisteredActions();
  }

  public void addAntConfigurationListener(final AntConfigurationListener listener) {
    myEventDispatcher.addListener(listener);
  }

  public void removeAntConfigurationListener(final AntConfigurationListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  public boolean isFilterTargets() {
    return myAntWorkspaceConfiguration.FILTER_TARGETS;
  }

  @Override
  public boolean isModuleGrouping() {
    return myAntWorkspaceConfiguration.MODULE_GROUPING;
  }

  public void setFilterTargets(final boolean value) {
    myAntWorkspaceConfiguration.FILTER_TARGETS = value;
  }

  @Override
  public void setModuleGrouping(boolean value) {
    myAntWorkspaceConfiguration.MODULE_GROUPING = value;
  }

  public AntBuildTarget[] getMetaTargets(final AntBuildFile buildFile) {
    final List<ExecutionEvent> events = getEventsByClass(ExecuteCompositeTargetEvent.class);
    if (events.size() == 0) {
      return AntBuildTargetBase.EMPTY_ARRAY;
    }
    final List<AntBuildTargetBase> targets = new ArrayList<AntBuildTargetBase>();
    for (ExecutionEvent event : events) {
      final MetaTarget target = (MetaTarget)getTargetForEvent(event);
      if (target != null && buildFile.equals(target.getBuildFile())) {
        targets.add(target);
      }
    }
    return targets.toArray(new AntBuildTargetBase[targets.size()]);
  }

  public List<ExecutionEvent> getEventsForTarget(final AntBuildTarget target) {
    final List<ExecutionEvent> list = new ArrayList<ExecutionEvent>();
    synchronized (myEventToTargetMap) {
      for (final ExecutionEvent event : myEventToTargetMap.keySet()) {
        final AntBuildTarget targetForEvent = getTargetForEvent(event);
        if (target.equals(targetForEvent)) {
          list.add(event);
        }
      }
    }
    return list;
  }

  @Nullable
  public AntBuildTarget getTargetForEvent(final ExecutionEvent event) {
    final Pair<AntBuildFile, String> pair;
    synchronized (myEventToTargetMap) {
      pair = myEventToTargetMap.get(event);
    }
    if (pair == null) {
      return null;
    }
    final AntBuildFileBase buildFile = (AntBuildFileBase)pair.first;
    synchronized (myBuildFiles) {
      if (!myBuildFiles.contains(buildFile)) {
        return null; // file was removed
      }
    }
    final String targetName = pair.second;

    final AntBuildTarget antBuildTarget = buildFile.getModel().findTarget(targetName);
    if (antBuildTarget != null) {
      return antBuildTarget;
    }
    final List<ExecutionEvent> events = getEventsByClass(ExecuteCompositeTargetEvent.class);
    if (events.size() == 0) {
      return null;
    }
    for (ExecutionEvent ev : events) {
      final String name =
        ExecuteCompositeTargetEvent.TYPE_ID.equals(ev.getTypeId()) ? ((ExecuteCompositeTargetEvent)ev).getMetaTargetName() : ev.getPresentableName();
      if (Comparing.strEqual(targetName, name)) {
        return new MetaTarget(buildFile, ev.getPresentableName(), ((ExecuteCompositeTargetEvent)ev).getTargetNames());
      }
    }
    return null;
  }

  public void setTargetForEvent(final AntBuildFile buildFile, final String targetName, final ExecutionEvent event) {
    synchronized (myEventToTargetMap) {
      myEventToTargetMap.put(event, new Pair<AntBuildFile, String>(buildFile, targetName));
    }
  }

  public void clearTargetForEvent(final ExecutionEvent event) {
    synchronized (myEventToTargetMap) {
      myEventToTargetMap.remove(event);
    }
  }

  public void handleTargetRename(String oldTargetName, String newTargetName) {
    synchronized (myEventToTargetMap) {
      for (Map.Entry<ExecutionEvent, Pair<AntBuildFile, String>> entry : myEventToTargetMap.entrySet()) {
        final Pair<AntBuildFile, String> pair = entry.getValue();
        if (pair != null && Comparing.equal(pair.getSecond(), oldTargetName)) {
          entry.setValue(new Pair<AntBuildFile, String>(pair.getFirst(), newTargetName));
        }
      }
    }
  }

  public void updateBuildFile(final AntBuildFile buildFile) {
    myModificationCount++;
    myEventDispatcher.getMulticaster().buildFileChanged(buildFile);
    updateRegisteredActions();
  }

  public boolean isAutoScrollToSource() {
    return myAntWorkspaceConfiguration.IS_AUTOSCROLL_TO_SOURCE;
  }

  public void setAutoScrollToSource(final boolean value) {
    myAntWorkspaceConfiguration.IS_AUTOSCROLL_TO_SOURCE = value;
  }

  public Sdk getProjectDefaultAnt() {
    return DEFAULT_ANT.get(getProperties()).find(GlobalAntConfiguration.getInstance());
  }

  @Nullable
  public AntBuildModel getModelIfRegistered(final AntBuildFile buildFile) {
    synchronized (myBuildFiles) {
      if (!myBuildFiles.contains(buildFile)) {
        return null;
      }
    }
    return getModel(buildFile);
  }

  public long getModificationCount() {
    return myModificationCount;
  }

  private void readExternal(final Element parentNode) throws InvalidDataException {
    myIsInitialized = Boolean.FALSE;
    myAntWorkspaceConfiguration.loadFromProjectSettings(parentNode);
    getProperties().readExternal(parentNode);
    runWhenInitialized(new Runnable() {
      public void run() {
        loadBuildFileProjectProperties(parentNode);
      }
    });
  }

  private void runWhenInitialized(final Runnable runnable) {
    if (getProject().isInitialized()) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          runnable.run();
        }
      });
    }
    else {
      myStartupManager.runWhenProjectIsInitialized(new Runnable() {
        public void run() {
          runnable.run();
        }
      });
    }
  }

  private void writeExternal(final Element parentNode) {
    getProperties().writeExternal(parentNode);
    try {
      AccessRule.read(() ->
                      {
                        for (final AntBuildFileBase buildFile : getBuildFiles()) {
                          final Element element = new Element(BUILD_FILE);
                          element.setAttribute(URL, buildFile.getVirtualFile().getUrl());
                          buildFile.writeProperties(element);
                          saveEvents(element, buildFile);
                          parentNode.addContent(element);
                        }
                        final List<consulo.virtualFileSystem.VirtualFile> files =
                          new ArrayList<consulo.virtualFileSystem.VirtualFile>(myAntFileToContextFileMap.keySet());
                        // sort in order to minimize changes
                        Collections.sort(files, new Comparator<VirtualFile>() {
                          public int compare(final consulo.virtualFileSystem.VirtualFile o1,
                                             final consulo.virtualFileSystem.VirtualFile o2) {
                            return o1.getUrl().compareTo(o2.getUrl());
                          }
                        });
                        for (VirtualFile file : files) {
                          final Element element = new Element(CONTEXT_MAPPING);
                          final VirtualFile contextFile = myAntFileToContextFileMap.get(file);
                          element.setAttribute(URL, file.getUrl());
                          element.setAttribute(CONTEXT, contextFile.getUrl());
                          parentNode.addContent(element);
                        }
                      });
    }
    catch (consulo.util.xml.serializer.WriteExternalException e) {
      LOG.error(e);
      throw e;
    }
    catch (RuntimeException e) {
      LOG.error(e);
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private void saveEvents(final Element element, final AntBuildFile buildFile) {
    List<Element> events = null;
    final Set<String> savedEvents = new HashSet<String>();
    synchronized (myEventToTargetMap) {
      for (final ExecutionEvent event : myEventToTargetMap.keySet()) {
        final Pair<AntBuildFile, String> pair = myEventToTargetMap.get(event);
        if (!buildFile.equals(pair.first)) {
          continue;
        }
        Element eventElement = new Element(EXECUTE_ON_ELEMENT);
        eventElement.setAttribute(EVENT_ELEMENT, event.getTypeId());
        eventElement.setAttribute(TARGET_ELEMENT, pair.second);

        final String id = event.writeExternal(eventElement, getProject());
        if (savedEvents.contains(id)) {
          continue;
        }
        savedEvents.add(id);

        if (events == null) {
          events = new ArrayList<Element>();
        }
        events.add(eventElement);
      }
    }

    if (events != null) {
      Collections.sort(events, EventElementComparator.INSTANCE);
      for (Element eventElement : events) {
        element.addContent(eventElement);
      }
    }
  }

  public AntBuildModel getModel(final AntBuildFile buildFile) {
    AntBuildModelBase model = myModelToBuildFileMap.get(buildFile);
    if (model == null) {
      model = createModel(buildFile);
      myModelToBuildFileMap.put(buildFile, model);
    }
    return model;
  }

  @Nullable
  public AntBuildFile findBuildFileByActionId(final String id) {
    for (AntBuildFile buildFile : getBuildFiles()) {
      AntBuildModelBase model = (AntBuildModelBase)buildFile.getModel();
      if (id.equals(model.getDefaultTargetActionId())) {
        return buildFile;
      }
      if (model.hasTargetWithActionId(id)) {
        return buildFile;
      }
    }
    return null;
  }

  private AntBuildModelBase createModel(final AntBuildFile buildFile) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      // otherwise commitAllDocuments() must have been called before the whole process was started
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }
    return new AntBuildModelImpl(buildFile);
  }

  private AntBuildFileBase addBuildFileImpl(final consulo.virtualFileSystem.VirtualFile file) throws AntNoFileException {
    PsiFile xmlFile = myPsiManager.findFile(file);
    if (!(xmlFile instanceof XmlFile)) {
      throw new AntNoFileException("the file is not an xml file", file);
    }
    AntSupport.markFileAsAntFile(file, xmlFile.getProject(), true);
    if (!AntDomFileDescription.isAntFile(((XmlFile)xmlFile))) {
      throw new AntNoFileException("the file is not recognized as an ANT file", file);
    }
    final AntBuildFileImpl buildFile = new AntBuildFileImpl((XmlFile)xmlFile, this);
    synchronized (myBuildFiles) {
      myBuildFilesArray = null;
      myBuildFiles.add(buildFile);
    }
    return buildFile;
  }

  private void updateRegisteredActions() {
    final Project project = getProject();
    if (project.isDisposed()) {
      return;
    }
    final List<Pair<String, AnAction>> actionList = new ArrayList<Pair<String, AnAction>>();
    for (final AntBuildFile buildFile : getBuildFiles()) {
      final AntBuildModelBase model = (AntBuildModelBase)buildFile.getModel();
      String defaultTargetActionId = model.getDefaultTargetActionId();
      if (defaultTargetActionId != null) {
        final TargetAction action =
          new TargetAction(buildFile, TargetAction.DEFAULT_TARGET_NAME, new String[]{TargetAction.DEFAULT_TARGET_NAME}, null);
        actionList.add(new Pair<String, AnAction>(defaultTargetActionId, action));
      }

      collectTargetActions(model.getFilteredTargets(), actionList, buildFile);
      collectTargetActions(getMetaTargets(buildFile), actionList, buildFile);
    }

    synchronized (this) {
      // unregister Ant actions
      ActionManager actionManager = ActionManager.getInstance();
      final String[] oldIds = actionManager.getActionIds(AntConfiguration.getActionIdPrefix(project));
      for (String oldId : oldIds) {
        actionManager.unregisterAction(oldId);
      }
      final Set<String> registeredIds = new HashSet<>();
      for (Pair<String, AnAction> pair : actionList) {
        if (!registeredIds.contains(pair.first)) {
          registeredIds.add(pair.first);
          actionManager.registerAction(pair.first, pair.second);
        }
      }
    }
  }

  private static void collectTargetActions(final AntBuildTarget[] targets,
                                           final List<Pair<String, AnAction>> actionList,
                                           final AntBuildFile buildFile) {
    for (final AntBuildTarget target : targets) {
      final String actionId = ((AntBuildTargetBase)target).getActionId();
      if (actionId != null) {
        final TargetAction action =
          new TargetAction(buildFile, target.getName(), new String[]{target.getName()}, target.getNotEmptyDescription());
        actionList.add(new Pair<String, AnAction>(actionId, action));
      }
    }
  }

  private void removeBuildFileImpl(AntBuildFile buildFile) {
    final XmlFile antFile = buildFile.getAntFile();
    if (antFile != null) {
      AntSupport.markFileAsAntFile(antFile.getOriginalFile().getVirtualFile(), antFile.getProject(), false);
    }
    synchronized (myBuildFiles) {
      myBuildFilesArray = null;
      myBuildFiles.remove(buildFile);
    }
    myModelToBuildFileMap.remove(buildFile);
    myEventDispatcher.getMulticaster().buildFileRemoved(buildFile);
  }

  public boolean executeTargetBeforeCompile(final DataContext context) {
    return runTargetSynchronously(context, ExecuteBeforeCompilationEvent.getInstance());
  }

  public boolean executeTargetAfterCompile(final DataContext context) {
    return runTargetSynchronously(context, ExecuteAfterCompilationEvent.getInstance());
  }

  private boolean runTargetSynchronously(final DataContext dataContext, ExecutionEvent event) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      throw new IllegalStateException("Called in the event dispatch thread");
    }
    final AntBuildTarget target = getTargetForEvent(event);
    if (target == null) {
      // no task assigned
      return true;
    }
    return executeTargetSynchronously(dataContext, target);
  }

  public static boolean executeTargetSynchronously(final DataContext dataContext, final AntBuildTarget target) {
    return executeTargetSynchronously(dataContext, target, Collections.<BuildFileProperty>emptyList());
  }

  public static boolean executeTargetSynchronously(final DataContext dataContext,
                                                   final AntBuildTarget target,
                                                   final List<BuildFileProperty> additionalProperties) {
    final consulo.application.util.Semaphore targetDone = new Semaphore();
    final boolean[] result = new boolean[1];
    try {
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {

        public void run() {
          Project project = dataContext.getData(PlatformDataKeys.PROJECT);
          if (project == null || project.isDisposed()) {
            result[0] = false;
            return;
          }
          targetDone.down();
          target.run(dataContext, additionalProperties, new AntBuildListener() {
            public void buildFinished(int state, int errorCount) {
              result[0] = (state == AntBuildListener.FINISHED_SUCCESSFULLY) && (errorCount == 0);
              targetDone.up();
            }
          });
        }
      }, Application.get().getNoneModalityState());
    }
    catch (Exception e) {
      LOG.error(e);
      return false;
    }
    targetDone.waitFor();
    return result[0];
  }

  private List<ExecutionEvent> getEventsByClass(Class eventClass) {
    if (!myInitializing) {
      ensureInitialized();
    }
    final List<ExecutionEvent> list = new ArrayList<ExecutionEvent>();
    synchronized (myEventToTargetMap) {
      for (final ExecutionEvent event : myEventToTargetMap.keySet()) {
        if (eventClass.isInstance(event)) {
          list.add(event);
        }
      }
    }
    return list;
  }

  private void loadBuildFileProjectProperties(final Element parentNode) {
    final List<Pair<Element, VirtualFile>> files = new ArrayList<Pair<Element, VirtualFile>>();
    final consulo.virtualFileSystem.VirtualFileManager vfManager = consulo.virtualFileSystem.VirtualFileManager.getInstance();
    for (final Object o : parentNode.getChildren(BUILD_FILE)) {
      final Element element = (Element)o;
      final String url = element.getAttributeValue(URL);
      final consulo.virtualFileSystem.VirtualFile file = vfManager.findFileByUrl(url);
      if (file != null) {
        files.add(new Pair<Element, VirtualFile>(element, file));
      }
    }

    // contexts
    myAntFileToContextFileMap.clear();
    for (final Object o : parentNode.getChildren(CONTEXT_MAPPING)) {
      final Element element = (Element)o;
      final String url = element.getAttributeValue(URL);
      final String contextUrl = element.getAttributeValue(CONTEXT);
      final VirtualFile file = vfManager.findFileByUrl(url);
      final consulo.virtualFileSystem.VirtualFile contextFile = vfManager.findFileByUrl(contextUrl);
      if (file != null && contextFile != null) {
        myAntFileToContextFileMap.put(file, contextFile);
      }
    }

    final String title = AntBundle.message("loading.ant.config.progress");
    queueLater(new Task.Backgroundable(getProject(), title, false) {
      public void run(@Nonnull final ProgressIndicator indicator) {
        if (getProject().isDisposed()) {
          return;
        }
        indicator.setIndeterminate(true);
        indicator.pushState();
        try {
          indicator.setText(title);
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              try {
                myInitializing = true;
                // first, remove existing files
                final AntBuildFile[] currentFiles = getBuildFiles();
                for (AntBuildFile file : currentFiles) {
                  removeBuildFile(file);
                }
                // then fill the configuration with the files configured in xml
                List<Pair<Element, AntBuildFileBase>> buildFiles = new ArrayList<Pair<Element, AntBuildFileBase>>(files.size());
                for (Pair<Element, VirtualFile> pair : files) {
                  final Element element = pair.getFirst();
                  final VirtualFile file = pair.getSecond();
                  try {
                    final AntBuildFileBase buildFile = addBuildFileImpl(file);
                    buildFile.readProperties(element);
                    buildFiles.add(new Pair<Element, AntBuildFileBase>(element, buildFile));
                  }
                  catch (AntNoFileException ignored) {
                  }
                  catch (InvalidDataException e) {
                    LOG.error(e);
                  }
                }
                // updating properties separately to avoid  unnecesary building of PSI after clearing caches
                for (Pair<Element, AntBuildFileBase> pair : buildFiles) {
                  final AntBuildFileBase buildFile = pair.getSecond();
                  buildFile.updateProperties();
                  final VirtualFile vFile = buildFile.getVirtualFile();
                  final String buildFileUrl = vFile != null ? vFile.getUrl() : null;

                  for (final Object o1 : pair.getFirst().getChildren(EXECUTE_ON_ELEMENT)) {
                    final Element e = (Element)o1;
                    final String eventId = e.getAttributeValue(EVENT_ELEMENT);
                    ExecutionEvent event = null;
                    final String targetName = e.getAttributeValue(TARGET_ELEMENT);
                    if (ExecuteBeforeCompilationEvent.TYPE_ID.equals(eventId)) {
                      event = ExecuteBeforeCompilationEvent.getInstance();
                    }
                    else if (ExecuteAfterCompilationEvent.TYPE_ID.equals(eventId)) {
                      event = ExecuteAfterCompilationEvent.getInstance();
                    }
                    else if ("beforeRun".equals(eventId)) {
            /*
            for compatibility with previous format

                      <buildFile url="file://$PROJECT_DIR$/module/src/support-scripts.xml">
                        <executeOn event="beforeRun" target="prebuild-steps" runConfigurationType="Application" runConfigurationName="Main" />
                      </buildFile>
                      */
                      final String configType = e.getAttributeValue("runConfigurationType");
                      final String configName = e.getAttributeValue("runConfigurationName");
                      convertToBeforeRunTask(myProject, buildFileUrl, targetName, configType, configName);
                    }
                    else if (ExecuteCompositeTargetEvent.TYPE_ID.equals(eventId)) {
                      try {
                        event = new ExecuteCompositeTargetEvent(targetName);
                      }
                      catch (WrongNameFormatException e1) {
                        LOG.info(e1);
                        event = null;
                      }
                    }
                    if (event != null) {
                      try {
                        event.readExternal(e, (Project)getProject());
                        setTargetForEvent(buildFile, targetName, event);
                      }
                      catch (InvalidDataException readFailed) {
                        LOG.info(readFailed.getMessage());
                      }
                    }
                  }
                }
                AntWorkspaceConfiguration.getInstance((Project)getProject()).loadFileProperties();
              }
              catch (InvalidDataException e) {
                LOG.error(e);
              }
              finally {
                updateRegisteredActions();
                myInitializing = false;
                myIsInitialized = Boolean.TRUE;
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  public void run() {
                    myEventDispatcher.getMulticaster().configurationLoaded();
                  }
                });
              }
            }
          });
        }
        finally {
          indicator.popState();
        }
      }
    });
  }

  private static void convertToBeforeRunTask(ComponentManager project,
                                             String buildFileUrl,
                                             String targetName,
                                             String configType,
                                             String configName) {
    if (buildFileUrl == null || targetName == null || configType == null) {
      return;
    }
    final RunManager runManager = RunManager.getInstance((Project)project);
    final ConfigurationType type = ConfigurationTypeUtil.findConfigurationType(configType);
    if (type == null) {
      return;
    }
    if (configName != null) {
      for (RunConfiguration configuration : runManager.getConfigurations(type)) {
        if (configName.equals(configuration.getName())) {
          final List<AntBeforeRunTask> tasks = runManager.getBeforeRunTasks(configuration, AntBeforeRunTaskProvider.ID);
          if (!tasks.isEmpty()) {
            AntBeforeRunTask task = tasks.get(0);//This is legacy code, we had only one task that time
            task.setEnabled(true);
            task.setTargetName(targetName);
            task.setAntFileUrl(buildFileUrl);
          }
        }
      }
    }
    else {
      for (ConfigurationFactory factory : type.getConfigurationFactories()) {
        final RunConfiguration template = runManager.getConfigurationTemplate(factory).getConfiguration();
        final List<AntBeforeRunTask> tasks = runManager.getBeforeRunTasks(template, AntBeforeRunTaskProvider.ID);
        if (!tasks.isEmpty()) {
          AntBeforeRunTask task = tasks.get(0);//This is legacy code, we had only one task that time
          task.setEnabled(true);
          task.setTargetName(targetName);
          task.setAntFileUrl(buildFileUrl);
        }
      }
    }
  }

  private static void queueLater(final Task task) {
    final Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      task.queue();
    }
    else {
      app.invokeLater(new Runnable() {
        public void run() {
          task.queue();
        }
      });
    }
  }

  public void setContextFile(@Nonnull XmlFile file, @Nullable XmlFile context) {
    if (context != null) {
      myAntFileToContextFileMap.put(file.getVirtualFile(), context.getVirtualFile());
    }
    else {
      myAntFileToContextFileMap.remove(file.getVirtualFile());
    }
  }

  @Nullable
  public XmlFile getContextFile(@Nullable final XmlFile file) {
    if (file == null) {
      return null;
    }
    final VirtualFile context = myAntFileToContextFileMap.get(file.getVirtualFile());
    if (context == null) {
      return null;
    }
    final PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(context);
    if (!(psiFile instanceof XmlFile)) {
      return null;
    }
    final XmlFile xmlFile = (XmlFile)psiFile;
    return AntDomFileDescription.isAntFile(xmlFile) ? xmlFile : null;
  }

  @Nullable
  public AntBuildFileBase getAntBuildFile(@Nonnull PsiFile file) {
    final VirtualFile vFile = file.getVirtualFile();
    if (vFile != null) {
      for (AntBuildFileBase bFile : getBuildFiles()) {
        if (vFile.equals(bFile.getVirtualFile())) {
          return bFile;
        }
      }
    }
    return null;
  }

  @Nullable
  public XmlFile getEffectiveContextFile(final XmlFile file) {
    return new Object() {
      @Nullable
      XmlFile findContext(final XmlFile file, Set<PsiElement> processed) {
        if (file != null) {
          processed.add(file);
          final XmlFile contextFile = getContextFile(file);
          return (contextFile == null || processed.contains(contextFile)) ? file : findContext(contextFile, processed);
        }
        return null;
      }
    }.findContext(file, new HashSet<consulo.language.psi.PsiElement>());
  }

  private static class EventElementComparator implements Comparator<Element> {
    static final Comparator<? super Element> INSTANCE = new EventElementComparator();

    private static final String[] COMPARABLE_ATTRIB_NAMES = new String[]{
      EVENT_ELEMENT,
      TARGET_ELEMENT,
      ExecuteCompositeTargetEvent.PRESENTABLE_NAME
    };

    public int compare(final Element o1, final Element o2) {
      for (String attribName : COMPARABLE_ATTRIB_NAMES) {
        final int valuesEqual = Comparing.compare(o1.getAttributeValue(attribName), o2.getAttributeValue(attribName));
        if (valuesEqual != 0) {
          return valuesEqual;
        }
      }
      return 0;
    }
  }
}
