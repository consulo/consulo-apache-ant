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

import consulo.util.lang.Pair;
import java.util.HashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 22, 2010
 */
public class TargetResolver extends PropertyProviderFinder {

  private List<String> myDeclaredTargetRefs;
  private @Nullable AntDomTarget myContextTarget;

  private Result myResult;

  public static class Result {
    private String myRefsString;
    private Map<String, Pair<AntDomTarget, String>> myMap = new HashMap<String, Pair<AntDomTarget, String>>(); // declared target name -> pair[target, effective name]
    private Map<String, AntDomTarget> myVariants;

    void add(String declaredTargetRef, Pair<AntDomTarget, String> pair) {
      myMap.put(declaredTargetRef, pair);
    }

    void setVariants(Map<String, AntDomTarget> variants) {
      myVariants = variants;
    }

    public String getRefsString() {
      return myRefsString;
    }

    public void setRefsString(String refsString) {
      myRefsString = refsString;
    }

    @Nonnull
    public Collection<String> getTargetReferences() {
      return Collections.unmodifiableSet(myMap.keySet());
    }

    @Nullable
    public Pair<AntDomTarget, String> getResolvedTarget(String declaredTargetRef) {
      return myMap.get(declaredTargetRef);
    }

    @Nonnull
    public Map<String, AntDomTarget> getVariants() {
      return myVariants != null? myVariants : Collections.<String, AntDomTarget>emptyMap();
    }
  }

  private TargetResolver(@Nonnull Collection<String> declaredDependencyRefs, @Nullable AntDomTarget contextElement) {
    super(contextElement);
    myResult = new Result();
    myDeclaredTargetRefs = new ArrayList<String>(declaredDependencyRefs);
    myContextTarget = contextElement;
  }

  @Nonnull
  public static Result resolve(@Nonnull AntDomProject project, @Nullable AntDomTarget contextTarget, @Nonnull String declaredTargetRef) {
    return resolve(project, contextTarget, Arrays.asList(declaredTargetRef));
  }

  public static Result resolve(AntDomProject project, AntDomTarget contextTarget, @Nonnull Collection<String> declaredTargetRefs) {
    final TargetResolver resolver = new TargetResolver(declaredTargetRefs, contextTarget);
    resolver.execute(project, null);
    final Result result = resolver.getResult();
    result.setVariants(resolver.getDiscoveredTargets());
    return result;
  }
  
  public interface TargetSink {
    void duplicateTargetDetected(AntDomTarget existingTarget, AntDomTarget duplicatingTarget, String targetEffectiveName);
  }
  
  public static void validateDuplicateTargets(AntDomProject project, final TargetSink sink) {
    final TargetResolver resolver = new TargetResolver(Collections.<String>emptyList(), null) {
      protected void duplicateTargetFound(AntDomTarget existingTarget, AntDomTarget duplicatingTarget, String taregetEffectiveName) {
        sink.duplicateTargetDetected(existingTarget, duplicatingTarget, taregetEffectiveName);
      }

      protected void stageCompleted(Stage completedStage, Stage startingStage) {
        if (Stage.RESOLVE_MAP_BUILDING_STAGE.equals(completedStage)) {
          stop();
        }
      }
    };
    resolver.execute(project, null);
  }

  protected void targetDefined(AntDomTarget target, String targetEffectiveName, Map<String, Pair<AntDomTarget, String>> dependenciesMap) {
    if (myContextTarget != null && myDeclaredTargetRefs.size() > 0 && target.equals(myContextTarget)) {
      for (Iterator<String> it = myDeclaredTargetRefs.iterator(); it.hasNext();) {
        final String declaredRef = it.next();
        final Pair<AntDomTarget, String> result = dependenciesMap.get(declaredRef);
        if (result != null) {
          myResult.add(declaredRef, result);
          it.remove();
        }
      }
      stop();
    }
  }

  protected void stageCompleted(Stage completedStage, Stage startingStage) {
    if (completedStage == Stage.RESOLVE_MAP_BUILDING_STAGE) {
      if (myDeclaredTargetRefs.size() > 0) {
        for (Iterator<String> it = myDeclaredTargetRefs.iterator(); it.hasNext();) {
          final String declaredRef = it.next();
          final AntDomTarget result = getTargetByName(declaredRef);
          if (result != null) {
            myResult.add(declaredRef, new Pair<AntDomTarget, String>(result, declaredRef)); // treat declared name as effective name
            it.remove();
          }
        }
      }
      stop();
    }
  }

  @Nonnull
  public Result getResult() {
    return myResult;
  }

  @Override
  protected void propertyProviderFound(PropertiesProvider propertiesProvider) {
  }
}
