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

import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 22, 2010
 */

public class AntCallParamsFinder extends AntDomRecursiveVisitor {
  private final String myPropertyName;
  private final List<PsiElement> myResult = new ArrayList<PsiElement>();

  private AntCallParamsFinder(@Nonnull String propertyName) {
    myPropertyName = propertyName;
  }

  public void visitAntDomElement(AntDomElement element) {
    if (!element.isDataType()) { // optimization
      super.visitAntDomElement(element);
    }
  }

  public void visitAntDomAntCallParam(AntDomAntCallParam antCallParam) {
    if (myPropertyName.equals(antCallParam.getName().getStringValue())) {
      final PsiElement elem = antCallParam.getNavigationElement(myPropertyName);
      if (elem != null) {
        myResult.add(elem);
      }
    }
  }

  @Nonnull
  public static List<PsiElement> resolve(@Nonnull AntDomProject project, @Nonnull String propertyName) {
    final AntCallParamsFinder resolver = new AntCallParamsFinder(propertyName);
    project.accept(resolver);
    return resolver.myResult;

  }
}
