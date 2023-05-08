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
package com.intellij.lang.ant;

import consulo.annotation.component.ExtensionImpl;
import consulo.index.io.DataIndexer;
import consulo.index.io.EnumeratorIntegerDescriptor;
import consulo.index.io.ID;
import consulo.index.io.KeyDescriptor;
import consulo.language.psi.stub.FileBasedIndex;
import consulo.language.psi.stub.FileContent;
import consulo.language.psi.stub.ScalarIndexExtension;
import consulo.project.Project;
import consulo.util.io.Readers;
import consulo.util.xml.fastReader.NanoXmlUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.ide.highlighter.XmlFileType;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 28, 2008
 */
@ExtensionImpl
public class AntImportsIndex extends ScalarIndexExtension<Integer>{
  public static final consulo.index.io.ID<Integer, Void> INDEX_NAME = ID.create("ant-imports");
  private static final int VERSION = 5;
  public static final Integer ANT_FILES_WITH_IMPORTS_KEY = new Integer(0);
  
  private static final DataIndexer<Integer,Void,FileContent> DATA_INDEXER = new consulo.index.io.DataIndexer<Integer, Void, consulo.language.psi.stub.FileContent>() {
    @Override
    @Nonnull
    public Map<Integer, Void> map(final FileContent inputData) {
      final Map<Integer, Void> map = new HashMap<Integer, Void>();

      NanoXmlUtil.parse(Readers.readerFromCharSequence(inputData.getContentAsText()), new consulo.util.xml.fastReader.NanoXmlUtil.IXMLBuilderAdapter() {
        private boolean isFirstElement = true;
        @Override
        public void startElement(final String elemName, final String nsPrefix, final String nsURI, final String systemID, final int lineNr) throws Exception {
          if (isFirstElement) {
            if (!"project".equalsIgnoreCase(elemName)) {
              stop();
            }
            isFirstElement = false;
          }
          else {
            if ("import".equalsIgnoreCase(elemName) || "include".equalsIgnoreCase(elemName)) {
              map.put(ANT_FILES_WITH_IMPORTS_KEY, null);
              stop();
            }
          }
        }

        @Override
        public void addAttribute(final String key, final String nsPrefix, final String nsURI, final String value, final String type) throws Exception {
          //if (myAttributes != null) {
          //  myAttributes.add(key);
          //}
        }

        @Override
        public void elementAttributesProcessed(final String name, final String nsPrefix, final String nsURI) throws Exception {
          //if (myAttributes != null) {
          //  if (!(myAttributes.contains("name") && myAttributes.contains("default"))) {
          //    stop();
          //  }
          //  myAttributes = null;
          //}
        }

      });
      return map;
    }
  };
  private static final FileBasedIndex.InputFilter INPUT_FILTER = new FileBasedIndex.InputFilter() {
    @Override
    public boolean acceptInput(Project project, final VirtualFile file) {
      return file.getFileType() instanceof XmlFileType;
    }
  };

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  @Nonnull
  public consulo.index.io.ID<Integer, Void> getName() {
    return INDEX_NAME;
  }

  @Override
  @Nonnull
  public consulo.index.io.DataIndexer<Integer, Void, FileContent> getIndexer() {
    return DATA_INDEXER;
  }

  @Override
  public KeyDescriptor<Integer> getKeyDescriptor() {
    return EnumeratorIntegerDescriptor.INSTANCE;
  }

  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return INPUT_FILTER;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }
}
