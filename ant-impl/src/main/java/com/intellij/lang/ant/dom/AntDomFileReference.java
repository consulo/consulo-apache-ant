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

import com.intellij.lang.ant.AntBundle;
import consulo.document.util.TextRange;
import consulo.language.psi.path.FileReference;
import consulo.util.io.FileUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class AntDomFileReference extends FileReference implements AntDomReference{

  private boolean mySkipByAnnotator;

  public AntDomFileReference(final AntDomFileReferenceSet set, final TextRange range, final int index, final String text) {
    super(set, range, index, text);
  }

  @Nullable
  public String getText() {
    final AntDomFileReferenceSet refSet = getFileReferenceSet();
    final String _path = AntStringResolver.computeString(refSet.getAttributeValue(), super.getText());
    final String text = FileUtil.toSystemIndependentName(_path);
    return text.endsWith("/")? text.substring(0, text.length() - "/".length()) : text;
  }

  @Nonnull
  public Object[] getVariants() {
    return super.getVariants();
  }

  @Nonnull
  public AntDomFileReferenceSet getFileReferenceSet() {
    return (AntDomFileReferenceSet)super.getFileReferenceSet();
  }

  @Nonnull
  public String getUnresolvedMessagePattern() {
    return AntBundle.message("file.doesnt.exist", getCanonicalRepresentationText());
  }

  @Nullable
  public String getCanonicalRepresentationText() {
    final String value = getCanonicalText();
    return AntStringResolver.computeString(getFileReferenceSet().getAttributeValue(), value);
  }

  public boolean shouldBeSkippedByAnnotator() {
    return mySkipByAnnotator;
  }

  public void setShouldBeSkippedByAnnotator(boolean value) {
    mySkipByAnnotator = value;
  }
}