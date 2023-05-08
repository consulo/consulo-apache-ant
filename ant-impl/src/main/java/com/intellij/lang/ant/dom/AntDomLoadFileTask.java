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

import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.logging.Logger;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.util.xml.Attribute;
import consulo.xml.util.xml.Convert;
import consulo.xml.util.xml.GenericAttributeValue;

/**
 * @author Eugene Zhuravlev
 *         Date: Aug 6, 2010
 */
public abstract class AntDomLoadFileTask extends AntDomPropertyDefiningTask {

  private static final Logger LOG = Logger.getInstance(AntDomLoadFileTask.class);
  
  private String myCachedText;

  @Attribute("srcfile")
  @Convert(value = AntPathValidatingConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getSrcFile();

  @Attribute("encoding")
  public abstract GenericAttributeValue<String> getEncoding();
  
  protected String calcPropertyValue(String propertyName) {
    String text = myCachedText;
    if (text != null) {
      return text; 
    }
    final consulo.language.psi.PsiFileSystemItem file = getSrcFile().getValue();
    if (!(file instanceof consulo.language.psi.PsiFile)) {
      return "";
    }
    final VirtualFile vFile = ((PsiFile)file).getOriginalFile().getVirtualFile();
    if (vFile == null) {
      return "";
    }
    text = vFile.loadText().toString();
    myCachedText = text;
    return text;
  }
}
