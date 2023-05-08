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
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.ReflectedProject;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.lang.ant.config.impl.AntBuildFileImpl;
import com.intellij.lang.ant.config.impl.AntConfigurationImpl;
import com.intellij.lang.ant.config.impl.GlobalAntConfiguration;
import consulo.apache.ant.sdk.AntSdkClassLoaderUtil;
import consulo.apache.ant.util.AntJavaSdkUtil;
import consulo.application.util.SystemInfo;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTable;
import consulo.language.pom.PomService;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.logging.Logger;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.psi.xml.XmlElement;
import consulo.xml.psi.xml.XmlTag;
import consulo.xml.util.xml.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 * Date: Apr 6, 2010
 */
@SuppressWarnings({"AbstractClassNeverImplemented"})
@DefinesXml
public abstract class AntDomProject extends AntDomNamedElement implements PropertiesProvider {
  private static final Logger LOG = Logger.getInstance(AntDomProject.class);

  public static final String DEFAULT_ENVIRONMENT_PREFIX = "env.";

  private volatile ClassLoader myClassLoader;
  private volatile Map<String, String> myProperties;

  @Attribute("default")
  @Convert(value = AntDomDefaultTargetConverter.class)
  public abstract GenericAttributeValue<TargetResolver.Result> getDefaultTarget();

  @Attribute("basedir")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getBasedir();

  @Nullable
  public final consulo.language.psi.PsiFileSystemItem getProjectBasedir() {
    final consulo.language.psi.PsiFileSystemItem basedir = getBasedir().getValue();
    if (basedir != null) {
      return basedir;
    }
    final XmlTag tag = getXmlTag();
    final consulo.virtualFileSystem.VirtualFile containingFile = tag.getContainingFile().getOriginalFile().getVirtualFile();
    if (containingFile == null) {
      return null;
    }
    final consulo.virtualFileSystem.VirtualFile parent = containingFile.getParent();
    if (parent == null) {
      return null;
    }
    return tag.getManager().findDirectory(parent);
  }

  @Nullable
  public final String getProjectBasedirPath() {
    final String basedir = getBasedir().getStringValue();
    if (basedir != null) {
      final File file = new File(basedir);
      if (file.isAbsolute()) {
        try {
          return FileUtil.toSystemIndependentName(file.getCanonicalPath());
        }
        catch (IOException e) {
          LOG.info(e);
          return null;
        }
      }
    }
    final String selfDir = getContainingFileDir();
    if (basedir == null) {
      return selfDir;
    }
    // basedir is specified and is relative
    try {
      return FileUtil.toSystemIndependentName(new File(selfDir, basedir).getCanonicalPath());
    }
    catch (IOException e) {
      LOG.info(e);
      return null;
    }
  }

  @Nullable
  public final String getContainingFileDir() {
    final consulo.virtualFileSystem.VirtualFile containingFile = getXmlTag().getContainingFile().getOriginalFile().getVirtualFile();
    if (containingFile == null) {
      return null;
    }
    final VirtualFile parent = containingFile.getParent();
    return parent != null ? parent.getPath() : null;
  }

  @SubTagList("target")
  public abstract List<AntDomTarget> getDeclaredTargets();

  @SubTagList("import")
  public abstract List<AntDomImport> getDeclaredImports();

  @SubTagList("include")
  public abstract List<AntDomInclude> getDeclaredIncludes();

  @Nullable
  public final AntDomTarget findDeclaredTarget(String declaredName) {
    for (AntDomTarget target : getDeclaredTargets()) {
      if (declaredName.equals(target.getName().getRawText())) {
        return target;
      }
    }
    return null;
  }

  @Nonnull
  public final ClassLoader getClassLoader() {
    ClassLoader loader = myClassLoader;
    if (loader == null) {
      final XmlTag tag = getXmlTag();
      final consulo.language.psi.PsiFile containingFile = tag.getContainingFile();
      final AntBuildFileImpl buildFile =
        (AntBuildFileImpl)AntConfigurationBase.getInstance(containingFile.getProject()).getAntBuildFile(containingFile);
      if (buildFile != null) {
        loader = buildFile.getClassLoader();
      }
      else {
        Sdk antInstallation = getAntInstallation();
        loader = AntSdkClassLoaderUtil.getClassLoader(antInstallation);
      }
      myClassLoader = loader;
    }
    return loader;
  }

  public Sdk getAntInstallation() {
    final AntConfigurationBase configuration = AntConfigurationBase.getInstance(getXmlTag().getProject());
    Sdk antInstallation = null;
    if (configuration != null) {
      antInstallation = configuration.getProjectDefaultAnt();
    }
    if (antInstallation == null) {
      antInstallation = GlobalAntConfiguration.getInstance().findBundleAntBundle();
    }
    assert antInstallation != null;
    return antInstallation;
  }

  @Nullable
  public final Sdk getTargetJdk() {
    final XmlTag tag = getXmlTag();
    final PsiFile containingFile = tag.getContainingFile();
    final AntBuildFileImpl buildFile =
      (AntBuildFileImpl)AntConfigurationBase.getInstance(containingFile.getProject()).getAntBuildFile(containingFile);
    if (buildFile != null) {
      String jdkName = AntBuildFileImpl.CUSTOM_JDK_NAME.get(buildFile.getAllOptions());
      if (jdkName == null || jdkName.length() == 0) {
        jdkName = AntConfigurationImpl.DEFAULT_JDK_NAME.get(buildFile.getAllOptions());
      }
      if (jdkName != null && jdkName.length() > 0) {
        return SdkTable.getInstance().findSdk(jdkName);
      }
    }
    return AntJavaSdkUtil.getBundleSdk();
  }

  @Nonnull
  public Iterator<String> getNamesIterator() {
    return getProperties().keySet().iterator();
  }

  @Nullable
  public String getPropertyValue(String propertyName) {
    return getProperties().get(propertyName);
  }

  @Nullable
  public PsiElement getNavigationElement(String propertyName) {
    final DomTarget target = DomTarget.getTarget(this);
    final PsiElement nameElementPsi = target != null ? PomService.convertToPsi(target) : null;
    if (nameElementPsi != null) {
      return nameElementPsi;
    }
    final XmlElement xmlElement = getXmlElement();
    return xmlElement != null ? xmlElement.getNavigationElement() : null;
  }

  private Map<String, String> getProperties() {
    Map<String, String> properties = myProperties;
    if (properties == null) {
      final ReflectedProject reflected = ReflectedProject.getProject(getClassLoader());
      Map<String, String> externals = Collections.emptyMap();
      final consulo.language.psi.PsiFile containingFile = getXmlTag().getContainingFile();
      if (containingFile != null) {
        final AntBuildFileImpl buildFile =
          (AntBuildFileImpl)AntConfigurationBase.getInstance(containingFile.getProject()).getAntBuildFile(containingFile);
        if (buildFile != null) {
          externals = buildFile.getExternalProperties();
        }
      }
      myProperties = (properties = loadPredefinedProperties(reflected.getProperties(), externals));
    }
    return properties;
  }

  @SuppressWarnings({"UseOfObsoleteCollectionType"})
  private Map<String, String> loadPredefinedProperties(final Hashtable properties, final Map<String, String> externalProps) {
    final Map<String, String> destination = new HashMap<String, String>();
    if (properties != null) {
      final Enumeration props = properties.keys();
      while (props.hasMoreElements()) {
        final String name = (String)props.nextElement();
        final String value = (String)properties.get(name);
        appendProperty(destination, name, value);
      }
    }
    //final Map<String, String> envMap = System.getenv();
    //for (final String name : envMap.keySet()) {
    //  if (name.length() > 0) {
    //    final String value = envMap.get(name);
    //    appendProperty(destination, DEFAULT_ENVIRONMENT_PREFIX + name, value);
    //  }
    //}
    if (externalProps != null) {
      for (final String name : externalProps.keySet()) {
        final String value = externalProps.get(name);
        appendProperty(destination, name, value);
      }
    }

    String basedir = getProjectBasedirPath();
    if (basedir == null) {
      basedir = "";
    }
    if (!FileUtil.isAbsolute(basedir)) {
      final String containigFileDir = getContainingFileDir();
      if (containigFileDir != null) {
        try {
          basedir = new File(containigFileDir, basedir).getCanonicalPath();
        }
        catch (IOException e) {
          // ignore
        }
      }
    }
    if (basedir != null) {
      appendProperty(destination, "basedir", FileUtil.toSystemIndependentName(basedir));
    }

    final Sdk installation = getAntInstallation();
    final String homeDir = installation.getHomePath();
    if (homeDir != null) {
      appendProperty(destination, "ant.home", FileUtil.toSystemIndependentName(homeDir));
    }
    appendProperty(destination, "ant.version", installation.getVersionString());

    final String projectName = getName().getRawText();
    appendProperty(destination, "ant.project.name", (projectName == null) ? "" : projectName);

    final Sdk jdkToRunWith = getTargetJdk();
    final String version = jdkToRunWith != null ? jdkToRunWith.getVersionString() : null;
    appendProperty(destination, "ant.java.version", version != null ? version : SystemInfo.JAVA_VERSION);

    final consulo.virtualFileSystem.VirtualFile containingFile = getXmlTag().getContainingFile().getOriginalFile().getVirtualFile();
    if (containingFile != null) {
      final String antFilePath = containingFile.getPath();
      appendProperty(destination, "ant.file", antFilePath);
      if (projectName != null) {
        appendProperty(destination, "ant.file." + projectName, antFilePath);
        appendProperty(destination, "ant.file.type." + projectName, "file");
      }
    }
    return destination;
  }

  private static void appendProperty(final Map<String, String> map, String name, String value) {
    final String previous = map.put(name, value);
    if (previous != null) {
      map.put(name, previous);
    }
  }
}
