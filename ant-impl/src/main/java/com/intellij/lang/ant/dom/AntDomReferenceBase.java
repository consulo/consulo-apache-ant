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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.document.util.TextRange;
import consulo.language.pom.PomTarget;
import consulo.language.pom.PomTargetPsiElement;
import consulo.language.psi.PsiElement;
import consulo.xml.util.xml.DomElement;
import consulo.xml.util.xml.DomTarget;
import consulo.xml.util.xml.DomUtil;
import consulo.language.psi.PsiReferenceBase;

/**
 * @author Eugene Zhuravlev
 *         Date: Aug 13, 2010
 */
public abstract class AntDomReferenceBase extends PsiReferenceBase<PsiElement> implements AntDomReference{
  private boolean myShouldBeSkippedByAnnotator;
  protected AntDomReferenceBase(PsiElement element, TextRange range, boolean soft) {
    super(element, range, soft);
  }

  protected AntDomReferenceBase(consulo.language.psi.PsiElement element, TextRange range) {
    super(element, range);
  }

  protected AntDomReferenceBase(consulo.language.psi.PsiElement element, boolean soft) {
    super(element, soft);
  }

  protected AntDomReferenceBase(@Nonnull consulo.language.psi.PsiElement element) {
    super(element);
  }

  public boolean shouldBeSkippedByAnnotator() {
    return myShouldBeSkippedByAnnotator;
  }

  public void setShouldBeSkippedByAnnotator(boolean value) {
    myShouldBeSkippedByAnnotator = true;
  }
  
  @Nullable
  public static DomElement toDomElement(consulo.language.psi.PsiElement resolve) {
    if (resolve instanceof PomTargetPsiElement) {
      final PomTarget target = ((PomTargetPsiElement)resolve).getTarget();
      if(target instanceof DomTarget) {
        return ((DomTarget)target).getDomElement();
      }
      return null;
    }
    return DomUtil.getDomElement(resolve);
  }
  
}
