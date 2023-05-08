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

import consulo.application.AllIcons;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.ide.impl.idea.openapi.roots.ui.ModifiableCellAppearanceEx;
import consulo.ide.ui.CellAppearanceEx;
import consulo.ide.ui.FileAppearanceService;
import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.File;
import java.io.FileFilter;
import java.util.List;
import java.util.function.Function;

public class AllJarsUnderDirEntry implements AntClasspathEntry {
  @NonNls private static final String JAR_SUFFIX = ".jar";

  private static final Function<VirtualFile, AntClasspathEntry> CREATE_FROM_VIRTUAL_FILE = file -> fromVirtualFile(file);

  @NonNls static final String DIR = "dir";

  private final File myDir;

  public AllJarsUnderDirEntry(final File dir) {
    myDir = dir;
  }

  public AllJarsUnderDirEntry(final String osPath) {
    this(new File(osPath));
  }

  public void writeExternal(final Element dataElement) {
    String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, myDir.getAbsolutePath().replace(File.separatorChar, '/'));
    dataElement.setAttribute(DIR, url);
  }

  public void addFilesTo(final List<File> files) {
    File[] children = myDir.listFiles(new FileFilter() {
      public boolean accept(File pathName) {
        return pathName.getName().endsWith(JAR_SUFFIX) && pathName.isFile();
      }
    });
    if (children != null) ContainerUtil.addAll(files, children);
  }

  public CellAppearanceEx getAppearance() {
    CellAppearanceEx appearance = FileAppearanceService.getInstance().forIoFile(myDir);
    if (appearance instanceof ModifiableCellAppearanceEx) {
      ((ModifiableCellAppearanceEx)appearance).setIcon(AllIcons.Nodes.JarDirectory);
    }
    return appearance;
  }

  private static AntClasspathEntry fromVirtualFile(final VirtualFile file) {
    return new AllJarsUnderDirEntry(file.getPath());
  }

  @SuppressWarnings("ClassNameSameAsAncestorName")
  public static class AddEntriesFactory extends AntClasspathEntry.AddEntriesFactory {
    public AddEntriesFactory(final JComponent parentComponent) {
      super(parentComponent, FileChooserDescriptorFactory.createMultipleFoldersDescriptor(), CREATE_FROM_VIRTUAL_FILE);
    }
  }
}
