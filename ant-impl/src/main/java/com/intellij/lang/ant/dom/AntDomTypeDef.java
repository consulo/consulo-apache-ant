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

import java.util.List;

import consulo.apache.ant.ApacheAntClasses;
import consulo.xml.util.xml.Attribute;
import consulo.xml.util.xml.Convert;
import consulo.xml.util.xml.GenericAttributeValue;
import consulo.language.psi.PsiFileSystemItem;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 1, 2010
 */
public abstract class AntDomTypeDef extends AntDomCustomClasspathComponent{

  @Attribute("classname")
  public abstract GenericAttributeValue<String> getClassName();

  @Attribute("file")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<consulo.language.psi.PsiFileSystemItem> getFile();

  @Attribute("resource")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getResource();

  @Attribute("format")
  public abstract GenericAttributeValue<String> getFormat();

  @Attribute("adapter")
  public abstract GenericAttributeValue<String> getAdapter();

  @Attribute("adaptto")
  public abstract GenericAttributeValue<String> getAdaptto();

  public final boolean hasTypeLoadingErrors() {
    return CustomAntElementsRegistry.getInstance(getAntProject()).hasTypeLoadingErrors(this);
  }

  public final List<String> getErrorDescriptions() {
    return CustomAntElementsRegistry.getInstance(getAntProject()).getTypeLoadingErrors(this);
  }
  
  public boolean isTask(final Class clazz) {
    if ("taskdef".equals(getXmlTag().getName())) { // in taskdef, the adapter is always set to Task
      return true;
    }

    final String adaptto = getAdaptto().getStringValue();
    if (adaptto != null && isAssignableFrom(adaptto, clazz)) {
      return isAssignableFrom(ApacheAntClasses.Task, clazz);
    }

    final String adapter = getAdapter().getStringValue();
    if (adapter != null) {
      try {
        final Class adapterClass = clazz.getClassLoader().loadClass(adapter);
        return isAssignableFrom(ApacheAntClasses.Task, adapterClass);
      }
      catch (ClassNotFoundException ignored) {
      }
      catch (NoClassDefFoundError ignored) {
      }
      catch (UnsupportedClassVersionError ignored) {
      }
    }

    return isAssignableFrom(ApacheAntClasses.Task, clazz);
  }

  private static boolean isAssignableFrom(final String baseClassName, final Class clazz) {
    try {
      final ClassLoader loader = clazz.getClassLoader();
      if (loader != null) {
        final Class baseClass = loader.loadClass(baseClassName);
        return baseClass.isAssignableFrom(clazz);
      }
    }
    catch (ClassNotFoundException ignored) {
    }
    return false;
  }


}
