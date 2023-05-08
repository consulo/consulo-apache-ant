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
import consulo.language.editor.refactoring.rename.RenamePsiElementProcessor;
import consulo.language.pom.PomTarget;
import consulo.language.pom.PomTargetPsiElement;
import consulo.language.psi.PsiElement;
import consulo.util.lang.Trinity;
import consulo.xml.util.xml.DomElement;
import consulo.xml.util.xml.DomTarget;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 * Date: Aug 11, 2010
 */
@ExtensionImpl
public class AntRenameProcessor extends RenamePsiElementProcessor {

  public void prepareRenaming(PsiElement element, String newName, Map<consulo.language.psi.PsiElement, String> allRenames) {
    final AntDomElement antElement = convertToAntDomElement(element);
    String propName = null;
    if (antElement instanceof AntDomProperty) {
      propName = ((AntDomProperty)antElement).getName().getStringValue();
    }
    else if (antElement instanceof AntDomAntCallParam) {
      propName = ((AntDomAntCallParam)antElement).getName().getStringValue();
    }
    if (propName != null) {
      final AntDomProject contextProject = antElement.getContextAntProject();
      final List<consulo.language.psi.PsiElement> additional = AntCallParamsFinder.resolve(contextProject, propName);
      for (consulo.language.psi.PsiElement psiElement : additional) {
        allRenames.put(psiElement, newName);
      }
      if (antElement instanceof AntDomAntCallParam) {
        final Trinity<consulo.language.psi.PsiElement, Collection<String>, PropertiesProvider> result =
          PropertyResolver.resolve(contextProject, propName, null);
        if (result.getFirst() != null) {
          allRenames.put(result.getFirst(), newName);
        }
      }
    }
  }

  public boolean canProcessElement(@Nonnull consulo.language.psi.PsiElement element) {
    final AntDomElement antElement = convertToAntDomElement(element);
    if (antElement instanceof AntDomProperty || antElement instanceof AntDomAntCallParam) {
      return true;
    }
    return false;
  }

  @Nullable
  private static AntDomElement convertToAntDomElement(consulo.language.psi.PsiElement element) {
    if (element instanceof consulo.language.pom.PomTargetPsiElement) {
      final PomTarget target = ((PomTargetPsiElement)element).getTarget();
      if (target instanceof DomTarget) {
        final DomElement domElement = ((DomTarget)target).getDomElement();
        if (domElement instanceof AntDomElement) {
          return (AntDomElement)domElement;
        }
      }
    }
    return null;
  }
}
