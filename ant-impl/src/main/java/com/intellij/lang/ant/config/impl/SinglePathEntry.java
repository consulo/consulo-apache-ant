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

import consulo.fileChooser.FileChooserDescriptor;
import consulo.ide.ui.CellAppearanceEx;
import consulo.ide.ui.FileAppearanceService;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.util.VirtualFilePathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.File;
import java.util.List;
import java.util.function.Function;

public class SinglePathEntry implements AntClasspathEntry {
  private static final Function<VirtualFile, AntClasspathEntry> CREATE_FROM_VIRTUAL_FILE =
    singlePathEntry -> fromVirtualFile(singlePathEntry);

  @NonNls
  static final String PATH = "path";

  private File myFile;

  public SinglePathEntry(File file) {
    myFile = file;
  }

  public SinglePathEntry(final String osPath) {
    this(new File(osPath));
  }

  public void readExternal(final Element element) throws InvalidDataException {
    String value = element.getAttributeValue(PATH);
    myFile = new File(VirtualFilePathUtil.toPresentableUrl(value));
  }

  public void writeExternal(final Element element) {
    String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, myFile.getAbsolutePath().replace(File.separatorChar, '/'));
    element.setAttribute(PATH, url);
  }

  public void addFilesTo(final List<File> files) {
    files.add(myFile);
  }

  public CellAppearanceEx getAppearance() {
    return FileAppearanceService.getInstance().forIoFile(myFile);
  }

  private static SinglePathEntry fromVirtualFile(VirtualFile file) {
    return new SinglePathEntry(file.getPresentableUrl());
  }

  @SuppressWarnings("ClassNameSameAsAncestorName")
  public static class AddEntriesFactory extends AntClasspathEntry.AddEntriesFactory {
    public AddEntriesFactory(final JComponent parentComponent) {
      super(parentComponent, new FileChooserDescriptor(false, true, true, true, false, true), CREATE_FROM_VIRTUAL_FILE);
    }
  }
}
