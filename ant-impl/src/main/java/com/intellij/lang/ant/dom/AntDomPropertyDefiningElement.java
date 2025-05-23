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

import consulo.language.pom.PomService;
import consulo.language.psi.PsiElement;
import consulo.xml.psi.xml.XmlElement;
import consulo.xml.util.xml.DomTarget;
import consulo.xml.util.xml.GenericAttributeValue;

import jakarta.annotation.Nonnull;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Aug 12, 2010
 */
public abstract class AntDomPropertyDefiningElement extends AntDomElement implements PropertiesProvider {

  @Nonnull
  public final Iterator<String> getNamesIterator() {
    final List<GenericAttributeValue<String>> attribs = getPropertyDefiningAttributes();
    final List<String> result = new ArrayList<String>(attribs.size());
    for (GenericAttributeValue<String> attribValue : attribs) {
      final String name = attribValue.getStringValue();
      if (name != null && name.length() > 0) {
        result.add(name);
      }
    }
    for (String name : getImplicitPropertyNames()) {
      result.add(name);
    }
    return result.iterator();
  }

  public final PsiElement getNavigationElement(String propertyName) {
    for (GenericAttributeValue<String> value : getPropertyDefiningAttributes()) {
      if (!propertyName.equals(value.getStringValue())) {
        continue;
      }
      final DomTarget domTarget = DomTarget.getTarget(this, value);
      return domTarget != null? consulo.language.pom.PomService.convertToPsi(domTarget) : null;
    }
    
    for (String propName : getImplicitPropertyNames()) {
      if (propertyName.equals(propName)) {
        final DomTarget domTarget = DomTarget.getTarget(this);
        if (domTarget != null) {
          return PomService.convertToPsi(domTarget);
        }
        final XmlElement xmlElement = getXmlElement();
        return xmlElement != null? xmlElement.getNavigationElement() : null;
      }
    }
    return null;
  }
  
  public final String getPropertyValue(final String propertyName) {
    for (GenericAttributeValue<String> value : getPropertyDefiningAttributes()) {
      if (propertyName.equals(value.getStringValue())) {
        return calcPropertyValue(propertyName);
      }
    }
    for (String implicitPropName : getImplicitPropertyNames()) {
      if (propertyName.equals(implicitPropName)) {
        return calcPropertyValue(propertyName);
      }
    }
    return null;
  }

  protected List<GenericAttributeValue<String>> getPropertyDefiningAttributes() {
    return Collections.emptyList();
  }

  protected List<String> getImplicitPropertyNames() {
    return Collections.emptyList();
  }

  protected String calcPropertyValue(String propertyName) {
    return ""; // some non-null value; actual value can be determined at runtime only
  }
}
