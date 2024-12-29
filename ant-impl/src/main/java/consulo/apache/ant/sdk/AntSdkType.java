/*
 * Copyright 2013 Consulo.org
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
package consulo.apache.ant.sdk;

import consulo.annotation.component.ExtensionImpl;
import consulo.apache.ant.ApacheAntIcons;
import consulo.apache.ant.util.AntVersionUtil;
import consulo.container.plugin.PluginManager;
import consulo.content.OrderRootType;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkModificator;
import consulo.content.bundle.SdkType;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author VISTALL
 * @since 15:52/19.05.13
 */
@ExtensionImpl
public class AntSdkType extends SdkType {
  @Nonnull
  public static AntSdkType getInstance() {
    return EP_NAME.findExtensionOrFail(AntSdkType.class);
  }

  public AntSdkType() {
    super("APACHE_ANT");
  }

  @Nonnull
  @Override
  public Collection<String> suggestHomePaths() {
    consulo.virtualFileSystem.VirtualFile fileByIoFile =
      LocalFileSystem.getInstance().findFileByIoFile(PluginManager.getPluginPath(AntSdkType.class));
    if (fileByIoFile == null) {
      return Collections.emptyList();
    }
    VirtualFile dist = fileByIoFile.findChild("bundles");
    if (dist == null) {
      return Collections.emptyList();
    }
    consulo.virtualFileSystem.VirtualFile[] children = dist.getChildren();
    List<String> list = new ArrayList<String>(children.length);
    for (VirtualFile child : children) {
      list.add(child.getPath());
    }
    return list;
  }

  @Override
  public boolean canCreatePredefinedSdks() {
    return true;
  }

  @Override
  public void setupSdkPaths(Sdk sdk) {
    SdkModificator sdkModificator = sdk.getSdkModificator();
    VirtualFile homeDirectory = sdk.getHomeDirectory();
    VirtualFile lib = homeDirectory.findChild("lib");
    if (lib != null) {
      for (consulo.virtualFileSystem.VirtualFile virtualFile : lib.getChildren()) {
        VirtualFile archiveRootForLocalFile = ArchiveVfsUtil.getArchiveRootForLocalFile(virtualFile);
        if (archiveRootForLocalFile != null) {
          sdkModificator.addRoot(archiveRootForLocalFile, BinariesOrderRootType.getInstance());
        }
      }
    }
    sdkModificator.commitChanges();
  }

  @Override
  public Image getIcon() {
    return ApacheAntIcons.AntInstallation;
  }

  @Override
  public boolean isValidSdkHome(String path) {
    return AntVersionUtil.getVersion(path) != null;
  }

  @Nullable
  @Override
  public String getVersionString(String sdkHome) {
    final String version = AntVersionUtil.getVersion(sdkHome);

    return version == null ? "0.0" : version;
  }

  @Override
  public String suggestSdkName(String currentSdkName, String sdkHome) {
    return getPresentableName() + " " + getVersionString(sdkHome);
  }

  @Override
  public boolean isRootTypeApplicable(OrderRootType type) {
    return type == BinariesOrderRootType.getInstance();
  }

  @Nonnull
  @Override
  public String getPresentableName() {
    return "Apache Ant";
  }
}
