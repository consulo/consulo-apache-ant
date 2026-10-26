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

import consulo.component.util.config.Externalizer;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserFactory;
import consulo.ui.ex.util.CellAppearanceEx;
import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFilePathUtil;
import org.jdom.Element;

import javax.swing.*;
import java.io.File;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;

public interface AntClasspathEntry {
  Externalizer<AntClasspathEntry> EXTERNALIZER = new Externalizer<AntClasspathEntry>() {
    public AntClasspathEntry readValue(Element dataElement) {
      String pathUrl = dataElement.getAttributeValue(SinglePathEntry.PATH);
      if (pathUrl != null)
        return new SinglePathEntry(VirtualFilePathUtil.toPresentableUrl(pathUrl));
      String dirUrl = dataElement.getAttributeValue(AllJarsUnderDirEntry.DIR);
      if (dirUrl != null)
        return new AllJarsUnderDirEntry(VirtualFilePathUtil.toPresentableUrl(dirUrl));
      throw new IllegalStateException();
    }

    public void writeValue(Element dataElement, AntClasspathEntry entry)  {
      entry.writeExternal(dataElement);
    }
  };

  void writeExternal(Element dataElement);

  void addFilesTo(List<File> files);

  CellAppearanceEx getAppearance();

  abstract class AddEntriesFactory implements Supplier<CompletableFuture<List<AntClasspathEntry>>> {
    private final JComponent myParentComponent;
    private final FileChooserDescriptor myDescriptor;
    private final Function<VirtualFile,AntClasspathEntry> myMapper;

    public AddEntriesFactory(final JComponent parentComponent,
                             final FileChooserDescriptor descriptor,
                             final Function<VirtualFile, AntClasspathEntry> mapper) {
      myParentComponent = parentComponent;
      myDescriptor = descriptor;
      myMapper = mapper;
    }

    public CompletableFuture<List<AntClasspathEntry>> get() {
      return FileChooserFactory.getInstance()
        .newBuilder(myDescriptor)
        .parent(myParentComponent)
        .showMultipleAsVirtualFiles()
        .thenApply(files -> ContainerUtil.map(files, myMapper));
    }
  }
}
