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
package com.intellij.lang.ant.config.impl;

import com.dslplatform.json.CompiledJson;
import com.intellij.lang.ant.config.AntBuildTarget;
import consulo.execution.BeforeRunTask;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.annotation.Attribute;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: May 18, 2009
 */
@CompiledJson
public class AntBeforeRunTask extends BeforeRunTask {
  private @Nullable String myTargetName;
  private @Nullable String myAntFileUrl;

  @Attribute("antfile")
  public @Nullable String getAntFileUrl() {
    return myAntFileUrl;
  }

  public void setAntFileUrl(@Nullable String url) {
    myAntFileUrl = StringUtil.nullize(url, true);
  }

  @Attribute("target")
  public @Nullable String getTargetName() {
    return myTargetName;
  }

  public void setTargetName(@Nullable String targetName) {
    myTargetName = StringUtil.nullize(targetName, true);
  }

  public void setTarget(@Nullable AntBuildTarget target) {
    VirtualFile file = target == null ? null : target.getModel().getBuildFile().getVirtualFile();
    setAntFileUrl(file == null ? null : file.getUrl());
    setTargetName(file == null ? null : target.getName());
  }

  public boolean isRunningTarget(AntBuildTarget target) {
    final VirtualFile vFile = target.getModel().getBuildFile().getVirtualFile();
    if (vFile == null) {
      return false;
    }
    if (myAntFileUrl == null || !FileUtil.pathsEqual(myAntFileUrl, vFile.getUrl())) {
      return false;
    }
    return Comparing.equal(myTargetName, target.getName());
  }
}
