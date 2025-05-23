/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.lang.ant.AntBundle;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.ide.impl.idea.codeInsight.daemon.impl.HectorComponent;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;

import jakarta.annotation.Nonnull;

/**
 * @author Eugene Zhuravlev
 * Date: May 12, 2008
 */
public class AntChangeContextFix extends BaseIntentionAction {
  public AntChangeContextFix() {
    setText(AntBundle.message("intention.configure.highlighting.text"));
  }

  @Nonnull
  public final String getFamilyName() {
    return AntBundle.message("intention.configure.highlighting.family.name");
  }

  public boolean isAvailable(@Nonnull final Project project, final Editor editor, final PsiFile file) {
    //if (!(file instanceof XmlFile)) {
    //  return false;
    //}
    //final XmlTag xmlTag = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), XmlTag.class);
    //if (xmlTag == null) {
    //  return false;
    //}
    //final AntDomElement antDomElement = AntSupport.getAntDomElement(xmlTag);
    //if (antDomElement == null) {
    //  return false;
    //}
    return true;
  }

  public void invoke(@Nonnull final Project project,
                     final Editor editor,
                     final consulo.language.psi.PsiFile file) throws IncorrectOperationException {
    final consulo.ide.impl.idea.codeInsight.daemon.impl.HectorComponent component = new HectorComponent(file);
    //final JComponent focusComponent = findComponentToFocus(component);
    component.showComponent(EditorPopupHelper.getInstance().guessBestPopupLocation(editor));
  }

  //@Nullable
  //private static JComponent findComponentToFocus(final JComponent component) {
  //  if (component.getClientProperty(AntHectorConfigurable.CONTEXTS_COMBO_KEY) != null) {
  //    return component;
  //  }
  //  for (Component child : component.getComponents()) {
  //    if (child instanceof JComponent) {
  //      final JComponent found = findComponentToFocus((JComponent)child);
  //      if (found != null) {
  //        return found;
  //      }
  //    }
  //  }
  //  return null;
  //}
}
