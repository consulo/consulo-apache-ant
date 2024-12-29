/*
 * Copyright 2013-2018 consulo.io
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
package consulo.apache.ant.util;

import consulo.logging.Logger;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;

import jakarta.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Properties;

/**
 * @author VISTALL
 * @since 17:43/19.05.13
 */
public class AntVersionUtil {
  private static final Logger LOGGER = Logger.getInstance(AntVersionUtil.class);

  public static final String VERSION_RESOURCE = "org/apache/tools/ant/version.txt";
  private static final String PROPERTY_VERSION = "VERSION";

  @Nullable
  public static String getVersion(String path) {
    File file = new File(path, "lib/ant.jar");
    if (file.exists()) {
      try {
        final Properties properties = loadProperties(file);
        return properties.getProperty(PROPERTY_VERSION);
      }
      catch (IOException e) {
        LOGGER.warn(e);
      }
    }
    return null;
  }

  public static Properties loadProperties(File antJar) throws IOException {
    Properties properties = new Properties();

    consulo.virtualFileSystem.VirtualFile fileByIoFile = LocalFileSystem.getInstance().findFileByIoFile(antJar);
    if (fileByIoFile == null) {
      return properties;
    }
    consulo.virtualFileSystem.VirtualFile archiveRootForLocalFile = ArchiveVfsUtil.getArchiveRootForLocalFile(fileByIoFile);
    if (archiveRootForLocalFile == null) {
      return properties;
    }
    VirtualFile fileByRelativePath = archiveRootForLocalFile.findFileByRelativePath(VERSION_RESOURCE);
    if (fileByRelativePath == null) {
      return null;
    }
    properties.load(fileByRelativePath.getInputStream());

    return properties;
  }
}
