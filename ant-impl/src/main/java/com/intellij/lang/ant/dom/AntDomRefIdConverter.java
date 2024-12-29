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
import com.intellij.lang.ant.AntSupport;
import consulo.language.pom.PomService;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.ref.Ref;
import consulo.xml.util.xml.*;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 16, 2010
 */
public class AntDomRefIdConverter extends Converter<AntDomElement> implements CustomReferenceConverter<AntDomElement> {

  public AntDomElement fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s != null) {
      final AntDomElement element = AntSupport.getInvocationAntDomElement(context);
      if (element != null) {
        return findElementById(element.getContextAntProject(), s, CustomAntElementsRegistry.ourIsBuildingClasspathForCustomTagLoading.get());
      }
    }
    return null;
  }

  public String toString(@Nullable AntDomElement antDomElement, ConvertContext context) {
    return antDomElement != null? antDomElement.getId().getRawText() : null;
  }

  @Nonnull
  public consulo.language.psi.PsiReference[] createReferences(final GenericDomValue<AntDomElement> genericDomValue, final consulo.language.psi.PsiElement element, ConvertContext context) {
    final AntDomElement invocationElement = AntSupport.getInvocationAntDomElement(context);
    return new PsiReference[] {new AntDomReferenceBase(element, true) {
      public PsiElement resolve() {
        final AntDomElement value = genericDomValue.getValue();
        if (value == null) {
          return null;
        }
        final DomTarget target = DomTarget.getTarget(value, value.getId());
        if (target == null) {
          return null;
        }
        return PomService.convertToPsi(element.getProject(), target);
      }
      @Nonnull
      public Object[] getVariants() {
        if (invocationElement == null) {
          return ArrayUtil.EMPTY_OBJECT_ARRAY;
        }
        final Set<String> variants = new LinkedHashSet<String>();
        invocationElement.getContextAntProject().accept(new AntDomRecursiveVisitor() {
          public void visitAntDomElement(AntDomElement element) {
            final String variant = element.getId().getRawText();
            if (variant != null) {
              variants.add(variant);
            }
            super.visitAntDomElement(element);
          }
        });
        return variants.size() > 0 ? ArrayUtil.toObjectArray(variants) : ArrayUtil.EMPTY_OBJECT_ARRAY;
      }

      public String getUnresolvedMessagePattern() {
        return AntBundle.message("cannot.resolve.refid", getCanonicalText());
      }
    }};
  }

  @Nullable
  private static AntDomElement findElementById(AntDomElement from, final String id, final boolean skipCustomTags) {
    if (id.equals(from.getId().getRawText())) {
      return from;
    }
    final Ref<AntDomElement> result = new Ref<AntDomElement>(null);
    from.accept(new AntDomRecursiveVisitor() {
      public void visitAntDomCustomElement(AntDomCustomElement custom) {
        if (!skipCustomTags) {
          super.visitAntDomCustomElement(custom);
        }
      }

      public void visitAntDomElement(AntDomElement element) {
        if (result.get() != null) {
          return;
        }
        if (id.equals(element.getId().getRawText())) {
          result.set(element);
          return;
        }
        super.visitAntDomElement(element);
      }
    });
    
    return result.get();
  }
}
