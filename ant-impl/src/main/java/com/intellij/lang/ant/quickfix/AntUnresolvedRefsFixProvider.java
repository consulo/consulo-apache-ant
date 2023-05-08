/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.lang.ant.quickfix;

import com.intellij.lang.ant.dom.AntDomReference;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.QuickFixActionRegistrar;
import consulo.language.editor.intention.UnresolvedReferenceQuickFixProvider;
import consulo.language.psi.PsiReference;
import consulo.xml.psi.impl.source.xml.TagNameReference;

import javax.annotation.Nonnull;

/**
 * @author Eugene Zhuravlev
 *         Date: 6/23/12
 */
@ExtensionImpl
public class AntUnresolvedRefsFixProvider extends UnresolvedReferenceQuickFixProvider<PsiReference> {

  public void registerFixes(PsiReference ref, QuickFixActionRegistrar registrar) {
    if (ref instanceof TagNameReference || ref instanceof AntDomReference) {
      registrar.register(new AntChangeContextFix());
    }
  }

  @Nonnull
  public Class<PsiReference> getReferenceClass() {
    return consulo.language.psi.PsiReference.class;
  }
}
