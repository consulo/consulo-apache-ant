/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.lang.ant.ForcedAntFileAttribute;
import consulo.annotation.component.ExtensionImpl;
import consulo.apache.ant.ApacheAntIcons;
import consulo.component.util.Iconable;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.psi.xml.XmlDocument;
import consulo.xml.psi.xml.XmlFile;
import consulo.xml.psi.xml.XmlTag;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Eugene Zhuravlev
 * Date: Apr 6, 2010
 */
@ExtensionImpl
public class AntDomFileDescription extends AntFileDescription<AntDomProject> {
  private static final String ROOT_TAG_NAME = "project";

  public AntDomFileDescription() {
    super(AntDomProject.class, ROOT_TAG_NAME);
  }

  public boolean isMyFile(@Nonnull XmlFile file) {
    return super.isMyFile(file) && isAntFile(file);
  }

  @Nullable
  @Override
  public Image getFileIcon(@Iconable.IconFlags int flags) {
    return ApacheAntIcons.AntBuildXml;
  }

  public static boolean isAntFile(final XmlFile xmlFile) {
    final XmlDocument document = xmlFile.getDocument();
    if (document != null) {
      final XmlTag tag = document.getRootTag();
      final VirtualFile vFile = xmlFile.getOriginalFile().getVirtualFile();
      if (tag != null && ROOT_TAG_NAME.equals(tag.getName()) && tag.getContext() instanceof XmlDocument) {
        if (tag.getAttributeValue("name") != null && tag.getAttributeValue("default") != null && vFile != null && ForcedAntFileAttribute.mayBeAntFile(
          vFile)) {
          return true;
        }
      }
      if (vFile != null && ForcedAntFileAttribute.isAntFile(vFile)) {
        return true;
      }
    }
    return false;
  }

}
