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
package com.intellij.lang.ant.config.impl;

import consulo.apache.ant.sdk.AntSdkClassLoaderUtil;
import consulo.component.util.config.AbstractProperty;
import consulo.content.bundle.Sdk;
import consulo.logging.Logger;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class AntBuildFileClassLoaderHolder extends ClassLoaderHolder {
  private static final Logger LOG = Logger.getInstance(AntBuildFileClassLoaderHolder.class);

  protected final AbstractProperty.AbstractPropertyContainer myOptions;

  public AntBuildFileClassLoaderHolder(AbstractProperty.AbstractPropertyContainer options) {
    myOptions = options;
  }

  @Override
  protected ClassLoader buildClasspath() {
    final ArrayList<File> files = new ArrayList<File>();
    for (final AntClasspathEntry entry : AntBuildFileImpl.ADDITIONAL_CLASSPATH.get(myOptions)) {
      entry.addFilesTo(files);
    }

    final Sdk antInstallation = AntBuildFileImpl.RUN_WITH_ANT.get(myOptions);
    final ClassLoader parentLoader = (antInstallation != null) ? AntSdkClassLoaderUtil.getClassLoader(antInstallation) : null;
    if (parentLoader != null && files.size() == 0) {
      // no additional classpath, so it's ok to use ant installation's loader
      return parentLoader;
    }

    final List<URL> urls = new ArrayList<URL>(files.size());
    for (File file : files) {
      try {
        urls.add(file.toURI().toURL());
      }
      catch (MalformedURLException e) {
        LOG.debug(e);
      }
    }
    return new AntResourcesClassLoader(urls, parentLoader, false, false);
  }
}