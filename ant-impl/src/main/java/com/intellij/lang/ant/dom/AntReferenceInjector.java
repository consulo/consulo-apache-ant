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
import consulo.document.util.TextRange;
import consulo.language.psi.ElementManipulators;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.xml.psi.xml.XmlAttribute;
import consulo.xml.psi.xml.XmlAttributeValue;
import consulo.xml.util.xml.*;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 * Date: Apr 9, 2010
 */
@ExtensionImpl
public class AntReferenceInjector implements DomReferenceInjector {
  @Override
  public boolean isAvaliable(DomFileDescription<?> domFileDescription) {
    return domFileDescription instanceof AntFileDescription;
  }

  public String resolveString(@Nullable String unresolvedText, @Nonnull ConvertContext context) {
    // todo: speed optimization: disable string resolution in places where it is not applicable
    if (unresolvedText == null) {
      return null;
    }
    final DomElement element = context.getInvocationElement();
    return AntStringResolver.computeString(element, unresolvedText);
  }

  @Nonnull
  public PsiReference[] inject(@Nullable String unresolvedText, @Nonnull PsiElement element, @Nonnull ConvertContext context) {
    if (element instanceof XmlAttributeValue) {
      final XmlAttributeValue xmlAttributeValue = (XmlAttributeValue)element;
      final List<PsiReference> refs = new ArrayList<>();
      addPropertyReferences(context, xmlAttributeValue, refs);
      addMacrodefParameterRefs(xmlAttributeValue, refs);
      return refs.size() == 0 ? PsiReference.EMPTY_ARRAY : refs.toArray(new PsiReference[refs.size()]);
    }
    return PsiReference.EMPTY_ARRAY;
  }

  private static void addPropertyReferences(@Nonnull ConvertContext context,
                                            final XmlAttributeValue xmlAttributeValue,
                                            final Collection<PsiReference> result) {
    final String value = xmlAttributeValue.getValue();
    final DomElement contextElement = context.getInvocationElement();

    final XmlAttribute attrib = PsiTreeUtil.getParentOfType(xmlAttributeValue, XmlAttribute.class);
    if (attrib != null) {
      final String name = attrib.getName();
      if ("if".equals(name) || "unless".equals(name)) {
        // special handling of if/unless attributes
        final AntDomPropertyReference ref = new AntDomPropertyReference(
          contextElement, xmlAttributeValue, ElementManipulators.getValueTextRange(xmlAttributeValue)
        );
        // in runtime, if execution reaches this task the property is defined since it is used in if-condition
        // so it is would be a mistake to highlight this as unresolved prop
        ref.setShouldBeSkippedByAnnotator(true);
        result.add(ref);
        return;
      }
    }

    if (xmlAttributeValue != null /*&& value.indexOf("@{") < 0*/) {
      final int valueBeginingOffset =
        Math.abs(xmlAttributeValue.getTextRange().getStartOffset() - xmlAttributeValue.getValueTextRange().getStartOffset());
      int startIndex;
      int endIndex = -1;
      while ((startIndex = value.indexOf("${", endIndex + 1)) > endIndex) {
        if (startIndex > 0 && value.charAt(startIndex - 1) == '$') {
          // the '$' is escaped
          endIndex = startIndex + 1;
          continue;
        }
        startIndex += 2;
        endIndex = startIndex;
        int nestedBrackets = 0;
        while (value.length() > endIndex) {
          final char ch = value.charAt(endIndex);
          if (ch == '}') {
            if (nestedBrackets == 0) {
              break;
            }
            --nestedBrackets;
          }
          else if (ch == '{') {
            ++nestedBrackets;
          }
          ++endIndex;
        }
        if (nestedBrackets > 0 || endIndex > value.length()) {
          return;
        }
        if (endIndex >= startIndex) {
          //final String propName = value.substring(startIndex, endIndex);
          //if (antFile.isEnvironmentProperty(propName) && antFile.getProperty(propName) == null) {
          //  continue;
          //}
          final AntDomPropertyReference ref = new AntDomPropertyReference(
            contextElement, xmlAttributeValue, new TextRange(valueBeginingOffset + startIndex, valueBeginingOffset + endIndex)
          );

          result.add(ref);
        }
        endIndex = startIndex;
      }
    }
  }

  public static void addMacrodefParameterRefs(@Nonnull XmlAttributeValue element, final Collection<PsiReference> refs) {
    final DomElement domElement = DomUtil.getDomElement(element);
    if (domElement == null) {
      return;
    }
    final AntDomMacroDef macrodef = domElement.getParentOfType(AntDomMacroDef.class, true);
    if (macrodef == null) {
      return;
    }
    final String text = ElementManipulators.getValueText(element);
    final int valueBeginingOffset = Math.abs(element.getTextRange().getStartOffset() - element.getValueTextRange().getStartOffset());
    int startIndex;
    int endIndex = -1;
    while ((startIndex = text.indexOf("@{", endIndex + 1)) > endIndex) {
      startIndex += 2;
      endIndex = startIndex;
      int nestedBrackets = 0;
      while (text.length() > endIndex) {
        final char ch = text.charAt(endIndex);
        if (ch == '}') {
          if (nestedBrackets == 0) {
            break;
          }
          --nestedBrackets;
        }
        else if (ch == '{') {
          ++nestedBrackets;
        }
        ++endIndex;
      }
      if (nestedBrackets > 0 || endIndex == text.length()) {
        return;
      }
      if (endIndex >= startIndex) {
        //final String name = text.substring(startIndex, endIndex);
        refs.add(new AntDomMacrodefAttributeReference(element,
                                                      new TextRange(valueBeginingOffset + startIndex, valueBeginingOffset + endIndex)));
      }
      endIndex = startIndex;
    }
  }


}
