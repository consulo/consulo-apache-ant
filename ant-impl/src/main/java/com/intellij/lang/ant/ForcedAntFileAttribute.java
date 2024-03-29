/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import consulo.ide.impl.idea.buildfiles.ForcedBuildFileAttribute;
import consulo.logging.Logger;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.FileAttribute;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.NewVirtualFile;
import consulo.util.dataholder.Key;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 30, 2008
 */
public class ForcedAntFileAttribute extends FileAttribute
{
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.ForcedAntFileAttribute");

  private static final String ANT_ID = "ant";

  private static final ForcedAntFileAttribute ourAttribute = new ForcedAntFileAttribute();
  private static final Key<Boolean> ourAntFileMarker = Key.create("_forced_ant_attribute_");

  public ForcedAntFileAttribute() {
    super("_forced_ant_attribute_", 1, true);
  }

  public static boolean isAntFile(consulo.virtualFileSystem.VirtualFile file) {
    String id = consulo.ide.impl.idea.buildfiles.ForcedBuildFileAttribute.getFrameworkIdOfBuildFile(file);
    return ANT_ID.equals(id) || (consulo.util.lang.StringUtil.isEmpty(id) && isAntFileOld(file));
  }

  public static boolean mayBeAntFile(consulo.virtualFileSystem.VirtualFile file) {
    String id = consulo.ide.impl.idea.buildfiles.ForcedBuildFileAttribute.getFrameworkIdOfBuildFile(file);
    return StringUtil.isEmpty(id) || ANT_ID.equals(id);
  }

  private static boolean isAntFileOld(VirtualFile file) {
    if (file instanceof NewVirtualFile) {
      final DataInputStream is = ourAttribute.readAttribute(file);
      if (is != null) {
        try {
          try {
            return is.readBoolean();
          }
          finally {
            is.close();
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
      return false;
    }
    return Boolean.TRUE.equals(file.getUserData(ourAntFileMarker));
  }

  public static void forceAntFile(VirtualFile file, boolean value) {
    consulo.ide.impl.idea.buildfiles.ForcedBuildFileAttribute.forceFileToFramework(file, ANT_ID, value);
  }
}
