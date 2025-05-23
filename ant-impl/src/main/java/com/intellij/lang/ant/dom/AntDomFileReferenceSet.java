/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import consulo.application.util.SystemInfo;
import consulo.document.util.TextRange;
import consulo.language.psi.ElementManipulators;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.path.FileReference;
import consulo.language.psi.path.FileReferenceSet;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.ManagingFS;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.psi.xml.XmlAttributeValue;
import consulo.xml.util.xml.GenericAttributeValue;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;

public class AntDomFileReferenceSet extends FileReferenceSet {
  private final GenericAttributeValue myValue;

  public AntDomFileReferenceSet(final GenericAttributeValue attribValue, boolean validateFileRefs) {
    this(attribValue, attribValue.getRawText(), 0, validateFileRefs);
  }

  public AntDomFileReferenceSet(final GenericAttributeValue attribValue,
                                final String pathSubstring,
                                int beginOffset,
                                boolean validateFileRefs) {
    super(cutTrailingSlash(FileUtil.toSystemIndependentName(pathSubstring)),
          attribValue.getXmlAttributeValue(),
          ElementManipulators.getOffsetInElement(attribValue.getXmlAttributeValue()) +
            beginOffset,
          null,
          SystemInfo.isFileSystemCaseSensitive);
    myValue = attribValue;
    for (FileReference reference : getAllReferences()) {
      if (reference instanceof AntDomReference) {
        ((AntDomReference)reference).setShouldBeSkippedByAnnotator(!validateFileRefs);
      }
    }
  }

  public GenericAttributeValue getAttributeValue() {
    return myValue;
  }

  private static String cutTrailingSlash(String path) {
    if (path.endsWith("/")) {
      return path.substring(0, path.length() - 1);
    }
    return path;
  }

  @Override
  protected boolean isSoft() {
    return true;
  }

  @Override
  public FileReference createFileReference(final TextRange range, final int index, final String text) {
    return new AntDomFileReference(this, range, index, text);
  }

  @Nonnull
  @Override
  public XmlAttributeValue getElement() {
    return (XmlAttributeValue)super.getElement();
  }

  @Nullable
  @Override
  public String getPathString() {
    return myValue.getStringValue();
  }

  @Override
  public boolean isAbsolutePathReference() {
    if (super.isAbsolutePathReference()) {
      return true;
    }

    String path = getPathString();
    return path != null && FileUtil.isAbsolute(path);
  }

  @Nonnull
  @Override
  public Collection<PsiFileSystemItem> computeDefaultContexts() {
    final AntDomElement element = myValue.getParentOfType(AntDomElement.class, false);
    final AntDomProject containingProject = element != null ? element.getAntProject() : null;

    if (containingProject != null) {
      if (isAbsolutePathReference()) {
        return toFileSystemItems(ManagingFS.getInstance().getLocalRoots());
      }
      else {
        VirtualFile root = null;

        if (element instanceof AntDomAnt) {
          final PsiFileSystemItem dirValue = ((AntDomAnt)element).getAntFileDir().getValue();
          if (dirValue instanceof PsiDirectory) {
            root = dirValue.getVirtualFile();
          }
        }

        if (root == null) {
          final String basedir;
          if (element instanceof AntDomIncludingDirective) {
            basedir = containingProject.getContainingFileDir();
          }
          else {
            basedir = containingProject.getContextAntProject().getProjectBasedirPath();
          }
          if (basedir != null) {
            root = LocalFileSystem.getInstance().findFileByPath(basedir);
          }
        }

        if (root != null) {
          return toFileSystemItems(root);
        }
      }
    }

    return super.computeDefaultContexts();
  }
}