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

import consulo.xml.util.xml.Attribute;
import consulo.xml.util.xml.Convert;
import consulo.xml.util.xml.GenericAttributeValue;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFileSystemItem;

import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.Iterator;

/**
 * @author Eugene Zhuravlev
 *         Date: Aug 11, 2010
 */
public abstract class AntDomBuildnumberTask extends AntDomElement implements PropertiesProvider{
  public static final String PROPERTY_NAME = "build.number";

  @Attribute("file")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getFile();
  
  @Nonnull
  public Iterator<String> getNamesIterator() {
    return Collections.singletonList(PROPERTY_NAME).iterator();
  }

  public String getPropertyValue(String propertyName) {
    return PROPERTY_NAME.equals(propertyName)? "" : null;
  }

  public PsiElement getNavigationElement(String propertyName) {
    return PROPERTY_NAME.equals(propertyName)? getXmlElement() : null;
  }
}
