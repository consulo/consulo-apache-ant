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
import consulo.document.util.TextRange;
import consulo.language.editor.completion.AutoCompletionPolicy;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.pom.PomService;
import consulo.language.psi.*;
import consulo.language.psi.resolve.ResolveCache;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.text.StringTokenizer;
import consulo.xml.util.xml.DomElement;
import consulo.xml.util.xml.DomTarget;
import consulo.xml.util.xml.DomUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 * Date: Aug 17, 2010
 */
class AntDomTargetReference extends AntDomReferenceBase implements BindablePsiReference {

  private final ReferenceGroup myGroup;

  public AntDomTargetReference(consulo.language.psi.PsiElement element) {
    super(element, true);
    myGroup = null;
  }

  public AntDomTargetReference(PsiElement element, TextRange range, ReferenceGroup group) {
    super(element, range, true);
    myGroup = group;
    group.addReference(this);
  }

  public consulo.language.psi.PsiElement resolve() {
    return consulo.language.psi.resolve.ResolveCache.getInstance(getElement().getProject())
                                                    .resolveWithCaching(this, MyResolver.INSTANCE, false, false);
  }

  public consulo.language.psi.PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
    final DomElement targetDomElement = toDomElement(element);
    if (targetDomElement != null) {
      final AntDomTarget pointingToTarget = targetDomElement.getParentOfType(AntDomTarget.class, false);
      if (pointingToTarget != null) {
        // the aim here is to receive all variants available at this particular context
        final TargetResolver.Result result = doResolve(null);
        if (result != null) {
          final Map<String, AntDomTarget> variants = result.getVariants();
          String newName = null;
          if (!variants.isEmpty()) {
            List<Pair<String, String>> prefixNamePairs = null;
            for (Map.Entry<String, AntDomTarget> entry : variants.entrySet()) {
              final AntDomTarget candidateTarget = entry.getValue();
              if (pointingToTarget.equals(candidateTarget)) {
                final String candidateName = entry.getKey();
                final String candidateTargetName = candidateTarget.getName().getRawText();
                if (candidateName.endsWith(candidateTargetName)) {
                  final String prefix = candidateName.substring(0, candidateName.length() - candidateTargetName.length());
                  if (prefixNamePairs == null) {
                    prefixNamePairs = new ArrayList<Pair<String, String>>(); // lazy init
                  }
                  prefixNamePairs.add(new Pair<String, String>(prefix, candidateName));
                }
              }
            }
            final String currentRefText = getCanonicalText();
            for (Pair<String, String> pair : prefixNamePairs) {
              final String prefix = pair.getFirst();
              final String effectiveName = pair.getSecond();
              if (currentRefText.startsWith(prefix)) {
                if (newName == null || effectiveName.length() > newName.length()) {
                  // this candidate's prefix matches current reference text and this name is longer
                  // than the previous candidate, then prefer this name
                  newName = effectiveName;
                }
              }
            }
          }
          if (newName != null) {
            handleElementRename(newName);
            if (myGroup != null) {
              myGroup.textChanged(this, newName);
            }
          }
        }
      }
    }
    return getElement();
  }

  @Nullable
  private AntDomElement getHostingAntDomElement() {
    final DomElement selfElement = DomUtil.getDomElement(getElement());
    if (selfElement == null) {
      return null;
    }
    return selfElement.getParentOfType(AntDomElement.class, false);
  }

  @Nonnull
  public Object[] getVariants() {
    final TargetResolver.Result result = doResolve(getCanonicalText());
    if (result == null) {
      return EMPTY_ARRAY;
    }
    final Map<String, AntDomTarget> variants = result.getVariants();
    final List resVariants = new ArrayList();
    final Set<String> existing = getExistingNames();
    for (String s : variants.keySet()) {
      if (existing.contains(s)) {
        continue;
      }
      final LookupElementBuilder builder = LookupElementBuilder.create(s).withCaseSensitivity(false);
      final LookupElement element = AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE.applyPolicy(builder);
      resVariants.add(element);
    }
    return resVariants.toArray(new Object[resVariants.size()]);
  }

  @Nullable
  private TargetResolver.Result doResolve(@Nullable final String referenceText) {
    final AntDomElement hostingElement = getHostingAntDomElement();
    if (hostingElement == null) {
      return null;
    }
    AntDomProject projectToSearchFrom;
    AntDomTarget contextTarget;
    if (hostingElement instanceof AntDomAnt) {
      final PsiFileSystemItem antFile = ((AntDomAnt)hostingElement).getAntFilePath().getValue();
      projectToSearchFrom =
        antFile instanceof PsiFile ? AntSupport.getAntDomProjectForceAntFile((consulo.language.psi.PsiFile)antFile) : null;
      contextTarget = null;
    }
    else {
      projectToSearchFrom = hostingElement.getContextAntProject();
      contextTarget = hostingElement.getParentOfType(AntDomTarget.class, false);
    }
    if (projectToSearchFrom == null) {
      return null;
    }
    return TargetResolver.resolve(projectToSearchFrom,
                                  contextTarget,
                                  referenceText == null ? Collections.<String>emptyList() : Collections.singletonList(referenceText));
  }

  private Set<String> getExistingNames() {
    final AntDomElement hostingElement = getHostingAntDomElement();
    if (hostingElement == null) {
      return Collections.emptySet();
    }
    final AntDomTarget contextTarget = hostingElement.getParentOfType(AntDomTarget.class, false);
    if (contextTarget == null) {
      return Collections.emptySet();
    }
    final Set<String> existing = new LinkedHashSet<>();
    final String selfName = contextTarget.getName().getStringValue();
    if (selfName != null) {
      existing.add(selfName);
    }
    final String dependsString = contextTarget.getDependsList().getRawText();
    if (dependsString != null) {
      final StringTokenizer tokenizer = new consulo.util.lang.text.StringTokenizer(dependsString, ",", false);
      while (tokenizer.hasMoreTokens()) {
        existing.add(tokenizer.nextToken().trim());
      }
    }
    return existing;
  }

  public String getUnresolvedMessagePattern() {
    return AntBundle.message("cannot.resolve.target", getCanonicalText());
  }

  private static class MyResolver implements ResolveCache.Resolver {
    static final MyResolver INSTANCE = new MyResolver();

    public PsiElement resolve(@Nonnull PsiReference psiReference, boolean incompleteCode) {
      final TargetResolver.Result result = ((AntDomTargetReference)psiReference).doResolve(psiReference.getCanonicalText());
      if (result == null) {
        return null;
      }
      final Pair<AntDomTarget, String> pair = result.getResolvedTarget(psiReference.getCanonicalText());
      final DomTarget domTarget = pair != null && pair.getFirst() != null ? DomTarget.getTarget(pair.getFirst()) : null;
      return domTarget != null ? PomService.convertToPsi(domTarget) : null;
    }
  }

  public static class ReferenceGroup {
    private List<AntDomTargetReference> myRefs = new ArrayList<AntDomTargetReference>();

    public void addReference(AntDomTargetReference ref) {
      myRefs.add(ref);
    }

    public void textChanged(AntDomTargetReference ref, String newText) {
      Integer lengthDelta = null;
      for (AntDomTargetReference r : myRefs) {
        if (lengthDelta != null) {
          r.setRangeInElement(r.getRangeInElement().shiftRight(lengthDelta));
        }
        else if (r.equals(ref)) {
          final TextRange range = r.getRangeInElement();
          final int oldLength = range.getLength();
          lengthDelta = new Integer(newText.length() - oldLength);
          r.setRangeInElement(range.grown(lengthDelta));
        }
      }
    }
  }
}
