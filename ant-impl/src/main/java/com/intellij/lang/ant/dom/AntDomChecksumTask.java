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
import consulo.xml.util.xml.GenericAttributeValue;

import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Aug 6, 2010
 */
public abstract class AntDomChecksumTask extends AntDomPropertyDefiningTask {

  @Attribute("verifyproperty")
  public abstract GenericAttributeValue<String> getVerifyProperty();
  

  protected List<GenericAttributeValue<String>> getPropertyDefiningAttributes() {
    final GenericAttributeValue<String> verifyProperty = getVerifyProperty();
    if (verifyProperty.getRawText() != null) {
      return Collections.singletonList(verifyProperty);
    }
    return Collections.singletonList(getPropertyName());
  }
  
}
