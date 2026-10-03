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

import consulo.annotation.access.RequiredWriteAction;
import consulo.apache.ant.impl.localize.ApacheAntImplLocalize;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.fileEditor.FileEditorManager;
import consulo.ide.impl.idea.codeInsight.daemon.impl.HectorComponent;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.RelativePoint;
import jakarta.annotation.Nonnull;

/**
 * @author Eugene Zhuravlev
 * @since 2008-05-12
 */
public class AntChangeContextLocalFix implements LocalQuickFix {
    @Nonnull
    @Override
    public LocalizeValue getName() {
        return ApacheAntImplLocalize.intentionConfigureHighlightingText();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PsiElement psiElement = descriptor.getPsiElement();
        PsiFile containingFile = psiElement.getContainingFile();
        if (containingFile == null) {
            return;
        }
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
            return;
        }

        final HectorComponent component = new HectorComponent(containingFile.getOriginalFile());
        component.showComponent(RelativePoint.from(EditorPopupHelper.getInstance().guessBestPopupLocation(editor)));
    }
}
