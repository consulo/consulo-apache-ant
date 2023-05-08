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

import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.dom.AntDomFileDescription;
import consulo.component.util.config.*;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.dataContext.DataContext;
import consulo.ide.impl.idea.openapi.actionSystem.impl.SimpleDataContext;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.logging.Logger;
import consulo.pathMacro.Macro;
import consulo.pathMacro.MacroManager;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.util.lang.SystemProperties;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.xml.psi.xml.XmlFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.*;

public class AntBuildFileImpl implements AntBuildFileBase {

  private static final Logger LOG = Logger.getInstance(AntBuildFileImpl.class);
  @NonNls
  private static final String ANT_LIB = "/.ant/lib";
  private volatile Map<String, String> myCachedExternalProperties;
  private final Object myOptionsLock = new Object();

  public static final AbstractProperty<Sdk> ANT_INSTALLATION = new AbstractProperty<Sdk>() {
    @Override
    public String getName() {
      return "$antInstallation";
    }

    @Override
    public Sdk getDefault(final AbstractPropertyContainer container) {
      return GlobalAntConfiguration.INSTANCE.get(container).findBundleAntBundle();
    }

    @Override
    public Sdk copy(final Sdk value) {
      return value;
    }

    @Override
    public Sdk get(final AbstractPropertyContainer container) {
      if (container.hasProperty(ANT_REFERENCE)) {
        return RUN_WITH_ANT.get(container);
      }
      return GlobalAntConfiguration.INSTANCE.get(container).findBundleAntBundle();
    }
  };

  public static final AbstractProperty<List<File>> ALL_CLASS_PATH = new AbstractProperty<List<File>>() {
    @Override
    public String getName() {
      return "$allClasspath";
    }

    @Override
    public List<File> getDefault(AbstractProperty.AbstractPropertyContainer container) {
      return get(container);
    }

    @Override
    public List<File> get(AbstractProperty.AbstractPropertyContainer container) {
      List<File> classpath = new ArrayList<File>();
      collectClasspath(classpath, ADDITIONAL_CLASSPATH, container);
      Sdk antInstallation = ANT_INSTALLATION.get(container);
      if (antInstallation != null) {
        consulo.virtualFileSystem.VirtualFile[] virtualFiles =
          antInstallation.getRootProvider().getFiles(BinariesOrderRootType.getInstance());
        for (consulo.virtualFileSystem.VirtualFile virtualFile : virtualFiles) {
          consulo.virtualFileSystem.VirtualFile localFile = ArchiveVfsUtil.getVirtualFileForArchive(virtualFile);
          if (localFile != null) {
            classpath.add(VirtualFileUtil.virtualToIoFile(localFile));
          }
        }
      }

      return classpath;
    }

    private void collectClasspath(List<File> files,
                                  ListProperty<AntClasspathEntry> property,
                                  AbstractProperty.AbstractPropertyContainer container) {
      if (!container.hasProperty(property)) {
        return;
      }
      Iterator<AntClasspathEntry> entries = property.getIterator(container);
      while (entries.hasNext()) {
        AntClasspathEntry entry = entries.next();
        entry.addFilesTo(files);
      }
    }

    @Override
    public void set(AbstractProperty.AbstractPropertyContainer container, List<File> files) {
      throw new UnsupportedOperationException(getName());
    }

    @Override
    public List<File> copy(List<File> files) {
      return files;
    }
  };

  public static final BooleanProperty RUN_IN_BACKGROUND = new BooleanProperty("runInBackground", true);
  public static final IntProperty MAX_HEAP_SIZE = new IntProperty("maximumHeapSize", 128);
  public static final IntProperty MAX_STACK_SIZE = new IntProperty("maximumStackSize", 2);
  public static final BooleanProperty VERBOSE = new BooleanProperty("verbose", true);
  public static final BooleanProperty TREE_VIEW = new BooleanProperty("treeView", true);
  public static final BooleanProperty CLOSE_ON_NO_ERRORS = new BooleanProperty("viewClosedWhenNoErrors", false);
  public static final StringProperty CUSTOM_JDK_NAME =
    new StringProperty("customJdkName", "");
  public static final ListProperty<TargetFilter> TARGET_FILTERS =
    ListProperty.create("targetFilters");
  public static final ListProperty<BuildFileProperty> ANT_PROPERTIES =
    ListProperty.create("properties");
  public static final StringProperty ANT_COMMAND_LINE_PARAMETERS = new StringProperty("antCommandLine", "");
  public static final AbstractProperty<AntReference> ANT_REFERENCE =
    new ValueProperty<AntReference>("antReference", AntReference.PROJECT_DEFAULT);
  public static final ListProperty<AntClasspathEntry> ADDITIONAL_CLASSPATH =
    ListProperty.create("additionalClassPath");
  public static final AbstractProperty<Sdk> RUN_WITH_ANT = new AbstractProperty<Sdk>() {
    @Override
    public String getName() {
      return "$runWithAnt";
    }

    @Override
    @Nullable
    public Sdk getDefault(AbstractProperty.AbstractPropertyContainer container) {
      return get(container);
    }

    @Override
    @Nullable
    public Sdk get(AbstractProperty.AbstractPropertyContainer container) {
      return AntReference.findAnt(ANT_REFERENCE, container);
    }

    @Override
    public Sdk copy(Sdk antInstallation) {
      return antInstallation;
    }
  };

  private final consulo.virtualFileSystem.VirtualFile myVFile;
  private final Project myProject;
  private final AntConfigurationBase myAntConfiguration;
  private final ExternalizablePropertyContainer myWorkspaceOptions;
  private final ExternalizablePropertyContainer myProjectOptions;
  private final AbstractProperty.AbstractPropertyContainer myAllOptions;
  private final ClassLoaderHolder myClassloaderHolder;
  private boolean myShouldExpand = true;

  public AntBuildFileImpl(final XmlFile antFile, final AntConfigurationBase configuration) {
    myVFile = antFile.getOriginalFile().getVirtualFile();
    myProject = antFile.getProject();
    myAntConfiguration = configuration;
    myWorkspaceOptions = new ExternalizablePropertyContainer();
    myWorkspaceOptions.registerProperty(RUN_IN_BACKGROUND);
    myWorkspaceOptions.registerProperty(CLOSE_ON_NO_ERRORS);
    myWorkspaceOptions.registerProperty(TREE_VIEW);
    myWorkspaceOptions.registerProperty(VERBOSE);
    myWorkspaceOptions.registerProperty(TARGET_FILTERS, "filter", TargetFilter::new);

    myWorkspaceOptions.rememberKey(RUN_WITH_ANT);

    myProjectOptions = new ExternalizablePropertyContainer();
    myProjectOptions.registerProperty(MAX_HEAP_SIZE);
    myProjectOptions.registerProperty(MAX_STACK_SIZE);
    myProjectOptions.registerProperty(CUSTOM_JDK_NAME);
    myProjectOptions.registerProperty(ANT_COMMAND_LINE_PARAMETERS);
    myProjectOptions.registerProperty(ANT_PROPERTIES, "property", BuildFileProperty::new);
    myProjectOptions.registerProperty(ADDITIONAL_CLASSPATH, "entry", SinglePathEntry.EXTERNALIZER);
    myProjectOptions.registerProperty(ANT_REFERENCE, AntReference.EXTERNALIZER);

    myAllOptions = new CompositePropertyContainer(new AbstractProperty.AbstractPropertyContainer[]{
      myWorkspaceOptions,
      myProjectOptions,
      GlobalAntConfiguration.getInstance().getProperties(getProject())
    });

    myClassloaderHolder = new AntBuildFileClassLoaderHolder(myAllOptions);
  }

  public static List<File> getUserHomeLibraries() {
    ArrayList<File> classpath = new ArrayList<File>();
    final String homeDir = SystemProperties.getUserHome();
    new AllJarsUnderDirEntry(new File(homeDir, ANT_LIB)).addFilesTo(classpath);
    return classpath;
  }

  @Override
  @Nullable
  public String getPresentableName() {
    AntBuildModel model = myAntConfiguration.getModelIfRegistered(this);
    String name = model != null ? model.getName() : null;
    if (name == null || name.trim().length() == 0) {
      name = myVFile.getName();
    }
    return name;
  }

  @Override
  @Nullable
  public String getName() {
    final VirtualFile vFile = getVirtualFile();
    return vFile != null ? vFile.getName() : null;
  }


  @Override
  public AntBuildModelBase getModel() {
    return (AntBuildModelBase)myAntConfiguration.getModel(this);
  }

  @Override
  @Nullable
  public AntBuildModelBase getModelIfRegistered() {
    return (AntBuildModelBase)myAntConfiguration.getModelIfRegistered(this);
  }

  @Override
  public boolean isRunInBackground() {
    return RUN_IN_BACKGROUND.value(myAllOptions);
  }

  @Override
  @Nullable
  public XmlFile getAntFile() {
    final PsiFile psiFile = myVFile.isValid() ? PsiManager.getInstance(getProject()).findFile(myVFile) : null;
    if (!(psiFile instanceof XmlFile)) {
      return null;
    }
    final XmlFile xmlFile = (XmlFile)psiFile;
    return AntDomFileDescription.isAntFile(xmlFile) ? xmlFile : null;
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  @Nullable
  public consulo.virtualFileSystem.VirtualFile getVirtualFile() {
    return myVFile;
  }

  @Override
  public AbstractProperty.AbstractPropertyContainer getAllOptions() {
    return myAllOptions;
  }

  @Override
  @Nullable
  public String getPresentableUrl() {
    final consulo.virtualFileSystem.VirtualFile file = getVirtualFile();
    return (file == null) ? null : file.getPresentableUrl();
  }

  @Override
  public boolean shouldExpand() {
    return myShouldExpand;
  }

  @Override
  public void setShouldExpand(boolean expand) {
    myShouldExpand = expand;
  }

  @Override
  public boolean isTargetVisible(final AntBuildTarget target) {
    final TargetFilter filter = findFilter(target.getName());
    if (filter == null) {
      return target.isDefault() || target.getNotEmptyDescription() != null;
    }
    return filter.isVisible();
  }

  @Override
  public boolean exists() {
    final consulo.virtualFileSystem.VirtualFile file = getVirtualFile();
    if (file == null || !(new File(file.getPath()).exists())) {
      return false;
    }
    return true;
  }

  @Override
  public void updateProperties() {
    // do not change position
    final AntBuildTarget[] targets = getModel().getTargets();
    final Map<String, AntBuildTarget> targetByName = new LinkedHashMap<String, AntBuildTarget>(targets.length);
    for (AntBuildTarget target : targets) {
      String targetName = target.getName();
      if (targetName != null) {
        targetByName.put(targetName, target);
      }
    }

    synchronized (myOptionsLock) {
      myCachedExternalProperties = null;
      final ArrayList<TargetFilter> filters = TARGET_FILTERS.getModifiableList(myAllOptions);
      for (Iterator<TargetFilter> iterator = filters.iterator(); iterator.hasNext(); ) {
        final TargetFilter filter = iterator.next();
        final String name = filter.getTargetName();
        if (name == null) {
          iterator.remove();
        }
        else {
          AntBuildTarget target = targetByName.get(name);
          if (target != null) {
            filter.updateDescription(target);
            targetByName.remove(name);
          }
          else {
            iterator.remove();
          }
        }
      }
      // handle the rest of targets with non-null names
      for (AntBuildTarget target : targetByName.values()) {
        filters.add(TargetFilter.fromTarget(target));
      }
    }
  }

  @Override
  public void updateConfig() {
    basicUpdateConfig();
    DaemonCodeAnalyzer.getInstance(getProject()).restart();
  }

  @Override
  public void setTreeView(final boolean value) {
    TREE_VIEW.primSet(myAllOptions, value);
  }

  @Override
  public void setVerboseMode(final boolean value) {
    VERBOSE.primSet(myAllOptions, value);
  }

  @Override
  public boolean isViewClosedWhenNoErrors() {
    return CLOSE_ON_NO_ERRORS.value(myAllOptions);
  }

  @Override
  public void readWorkspaceProperties(final Element parentNode) throws InvalidDataException {
    synchronized (myOptionsLock) {
      myWorkspaceOptions.readExternal(parentNode);
      final Element expanded = parentNode.getChild("expanded");
      if (expanded != null) {
        myShouldExpand = Boolean.valueOf(expanded.getAttributeValue("value"));
      }

      // don't lose old command line parameters
      final Element antCommandLine = parentNode.getChild("antCommandLine");
      if (antCommandLine != null) {
        ANT_COMMAND_LINE_PARAMETERS.set(myProjectOptions, antCommandLine.getAttributeValue("value"));
      }
    }
  }

  @Override
  public void writeWorkspaceProperties(final Element parentNode) throws WriteExternalException {
    synchronized (myOptionsLock) {
      myWorkspaceOptions.writeExternal(parentNode);
      final Element expandedElem = new Element("expanded");
      expandedElem.setAttribute("value", Boolean.toString(myShouldExpand));
      parentNode.addContent(expandedElem);
    }
  }

  @Override
  public void readProperties(final Element parentNode) throws InvalidDataException {
    synchronized (myOptionsLock) {
      myProjectOptions.readExternal(parentNode);
      basicUpdateConfig();
      readWorkspaceProperties(parentNode); // Compatibility with old Idea
    }
  }

  @Override
  public void writeProperties(final Element parentNode) throws consulo.util.xml.serializer.WriteExternalException {
    synchronized (myOptionsLock) {
      myProjectOptions.writeExternal(parentNode);
    }
  }

  private void basicUpdateConfig() {
    final XmlFile antFile = getAntFile();
    if (antFile != null) {
      bindAnt();
      myClassloaderHolder.updateClasspath();
    }
  }

  @Override
  @Nonnull
  public Map<String, String> getExternalProperties() {
    Map<String, String> result = myCachedExternalProperties;
    if (result == null) {
      synchronized (myOptionsLock) {
        result = myCachedExternalProperties;
        if (result == null) {
          result = new HashMap<String, String>();

          final DataContext context = SimpleDataContext.getProjectContext(myProject);
          final MacroManager macroManager = MacroManager.getInstance();
          Iterator<BuildFileProperty> properties = ANT_PROPERTIES.getIterator(myAllOptions);
          while (properties.hasNext()) {
            BuildFileProperty property = properties.next();
            try {
              String value = property.getPropertyValue();
              value = macroManager.expandSilentMarcos(value, true, context);
              value = macroManager.expandSilentMarcos(value, false, context);
              result.put(property.getPropertyName(), value);
            }
            catch (Macro.ExecutionCancelledException e) {
              LOG.debug(e);
            }
          }
          myCachedExternalProperties = result;
        }
      }
    }
    return result;
  }

  private void bindAnt() {
    ANT_REFERENCE.set(getAllOptions(), ANT_REFERENCE.get(getAllOptions()).bind(GlobalAntConfiguration.getInstance()));
  }

  @Nullable
  private TargetFilter findFilter(final String targetName) {
    final List<TargetFilter> filters;
    synchronized (myOptionsLock) {
      filters = TARGET_FILTERS.get(myAllOptions);
    }
    for (TargetFilter targetFilter : filters) {
      if (Comparing.equal(targetName, targetFilter.getTargetName())) {
        return targetFilter;
      }
    }
    return null;
  }

  @Nonnull
  public ClassLoader getClassLoader() {
    return myClassloaderHolder.getClassloader();
  }
}
