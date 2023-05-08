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
package com.intellij.lang.ant.refactoring;

import com.intellij.lang.ant.dom.AntDomFileDescription;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.IndexNotReadyException;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.language.editor.LangDataKeys;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.refactoring.rename.PsiElementRenameHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.project.Project;
import consulo.xml.psi.xml.XmlFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2007
 */
@ExtensionImpl
public final class AntRenameHandler extends PsiElementRenameHandler {
  
  public boolean isAvailableOnDataContext(final DataContext dataContext) {
    final PsiElement[] elements = getElements(dataContext);
    return elements != null && elements.length > 1;
  }

  public void invoke(@Nonnull final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    final PsiElement[] elements = getElements(dataContext);
    if (elements != null && elements.length > 0) {
      invoke(project, new PsiElement[]{elements[0]}, dataContext);
    }
  }

  public void invoke(@Nonnull final Project project, @Nonnull final PsiElement[] elements, final DataContext dataContext) {
    super.invoke(project, elements, dataContext);
  }

  @Nullable 
  private static consulo.language.psi.PsiElement[] getElements(DataContext dataContext) {
    final PsiFile psiFile = dataContext.getData(LangDataKeys.PSI_FILE);
    if (!(psiFile instanceof XmlFile && AntDomFileDescription.isAntFile((XmlFile)psiFile))) {
      return null;
    }
    final Editor editor = dataContext.getData(LangDataKeys.EDITOR);
    if (editor == null) {
      return null;
    }
    return getPsiElementsIn(editor, psiFile);
  }
  
  @Nullable
  private static PsiElement[] getPsiElementsIn(final Editor editor, final PsiFile psiFile) {
    try {
      final PsiReference reference = consulo.language.editor.TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset());
      if (reference == null) {
        return null;
      }
      final Collection<PsiElement> candidates = TargetElementUtil.getTargetCandidates(reference);
      return candidates.toArray(new PsiElement[candidates.size()]);
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }
  
}
