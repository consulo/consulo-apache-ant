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
package com.intellij.lang.ant.validation;

import com.intellij.lang.ant.dom.AntDomFileDescription;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.HectorComponentPanel;
import consulo.language.editor.HectorComponentPanelsProvider;
import consulo.language.psi.PsiFile;
import consulo.xml.psi.xml.XmlFile;

import jakarta.annotation.Nonnull;

/**
 * @author Eugene Zhuravlev
 *         Date: May 12, 2008
 */
@ExtensionImpl
public class AntHectorPanelProvider implements HectorComponentPanelsProvider{
  public HectorComponentPanel createConfigurable(@Nonnull final PsiFile file) {
    if (file instanceof XmlFile && AntDomFileDescription.isAntFile(((XmlFile)file))) {
      return new AntHectorConfigurable(((XmlFile)file));
    }
    return null;
  }
}
