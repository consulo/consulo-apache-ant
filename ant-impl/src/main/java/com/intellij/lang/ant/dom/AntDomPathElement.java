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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import consulo.apache.ant.util.PathTokenizer;
import com.intellij.lang.ant.AntFilesProvider;
import consulo.language.psi.PsiFileSystemItem;
import consulo.xml.util.xml.Attribute;
import consulo.xml.util.xml.Convert;
import consulo.xml.util.xml.GenericAttributeValue;

/**
 * @author Eugene Zhuravlev
 *         Date: Jun 22, 2010
 */
public abstract class AntDomPathElement extends AntDomFilesProviderImpl{

  @Attribute("location")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getLocation();

  @Attribute("path")
  @Convert(value = AntMultiPathStringConverter.class)
  public abstract GenericAttributeValue<List<File>> getPath();

  
  @Nullable
  protected AntDomPattern getAntPattern() {
    return null; // not available
  }
  
  @Nonnull
  protected List<File> getFiles(AntDomPattern pattern, Set<AntFilesProvider> processed) {
    final List<File> files = new ArrayList<File>();
    final File baseDir = getCanonicalFile("");

    addLocation(baseDir, files, getLocation().getStringValue());

    final String pathString = getPath().getStringValue();
    if (pathString != null) {
      final PathTokenizer tokenizer = new PathTokenizer(pathString);
      while (tokenizer.hasMoreTokens()) {
        addLocation(baseDir, files, tokenizer.nextToken());
      }
    }

    return files;
  }

  private static void addLocation(final File baseDir, final List<File> files, final String locationPath) {
    if (locationPath != null) {
      File file = new File(locationPath);
      if (file.isAbsolute()) {
        files.add(file);
      }
      else {
        files.add(new File(baseDir, locationPath));
      }
    }
  }
}
