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

import consulo.annotation.component.ExtensionImpl;
import consulo.xml.psi.xml.XmlDocument;
import consulo.xml.psi.xml.XmlFile;
import consulo.xml.psi.xml.XmlTag;

import jakarta.annotation.Nonnull;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 6, 2010
 */
@ExtensionImpl
public class AntlibDomFileDescription extends AntFileDescription<AntDomAntlib> {
  private static final String ROOT_TAG_NAME = "antlib";

  public AntlibDomFileDescription() {
    super(AntDomAntlib.class, ROOT_TAG_NAME);
  }

  public boolean isMyFile(@Nonnull XmlFile file) {
    return super.isMyFile(file) && isAntLibFile(file);
  }

  public static boolean isAntLibFile(final XmlFile xmlFile) {
    final XmlDocument document = xmlFile.getDocument();
    if (document != null) {
      final XmlTag tag = document.getRootTag();
      return tag != null && ROOT_TAG_NAME.equals(tag.getName()) && tag.getContext() instanceof XmlDocument;
    }
    return false;
  }

}
