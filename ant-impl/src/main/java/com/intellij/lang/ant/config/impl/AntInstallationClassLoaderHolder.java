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

import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.logging.Logger;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class AntInstallationClassLoaderHolder extends ClassLoaderHolder {
  private static final Logger LOG = Logger.getInstance(AntInstallationClassLoaderHolder.class);
  private Sdk mySdk;

  public AntInstallationClassLoaderHolder(Sdk sdk) {
    mySdk = sdk;
  }

  @Override
  protected ClassLoader buildClasspath() {
    final ArrayList<File> files = new ArrayList<>();
    // ant installation jars
    consulo.virtualFileSystem.VirtualFile[] virtualFiles = mySdk.getRootProvider().getFiles(BinariesOrderRootType.getInstance());
    for (consulo.virtualFileSystem.VirtualFile virtualFile : virtualFiles) {
      VirtualFile localFile = ArchiveVfsUtil.getVirtualFileForArchive(virtualFile);
      if (localFile != null) {
        files.add(VfsUtil.virtualToIoFile(localFile));
      }
    }

    // jars from user home
    files.addAll(AntBuildFileImpl.getUserHomeLibraries());

    final List<URL> urls = new ArrayList<>(files.size());
    for (File file : files) {
      try {
        urls.add(file.toURI().toURL());
      }
      catch (MalformedURLException e) {
        LOG.debug(e);
      }
    }
    return new AntResourcesClassLoader(urls, null, true, false);
  }
}