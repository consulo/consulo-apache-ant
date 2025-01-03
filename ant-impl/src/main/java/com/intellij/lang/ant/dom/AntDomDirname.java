/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import jakarta.annotation.Nullable;

import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.xml.util.xml.Attribute;
import consulo.xml.util.xml.Convert;
import consulo.xml.util.xml.GenericAttributeValue;
import consulo.language.psi.PsiFileSystemItem;
import consulo.virtualFileSystem.VirtualFile;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 21, 2010
 */
public abstract class AntDomDirname extends AntDomPropertyDefiningTask {
  @Attribute("file")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getFile();

  @Nullable
  protected final String calcPropertyValue(String propertyName) {
    final consulo.language.psi.PsiFileSystemItem fsItem = getFile().getValue();
    if (fsItem != null) {
      final consulo.language.psi.PsiFileSystemItem parent = fsItem.getParent();
      if (parent != null) {
        final VirtualFile vFile = parent.getVirtualFile();
        if (vFile != null) {
          return consulo.ide.impl.idea.openapi.util.io.FileUtil.toSystemDependentName(vFile.getPath());
        }
      }
    }
    // according to the doc, defaulting to project's current dir
    final String projectBasedirPath = getContextAntProject().getProjectBasedirPath();
    if (projectBasedirPath == null) {
      return null;
    }
    return consulo.ide.impl.idea.openapi.util.io.FileUtil.toSystemDependentName(projectBasedirPath);
  }

}
