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
package org.mustbe.consulo.apache.ant.util;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import consulo.vfs.util.ArchiveVfsUtil;

/**
 * @author VISTALL
 * @since 17:43/19.05.13
 */
public class AntVersionUtil
{
	private static final Logger LOGGER = Logger.getInstance(AntVersionUtil.class);

	@NonNls
	public static final String VERSION_RESOURCE = "org/apache/tools/ant/version.txt";
	@NonNls
	private static final String PROPERTY_VERSION = "VERSION";

	@Nullable
	public static String getVersion(String path)
	{
		File file = new File(path, "lib/ant.jar");
		if(file.exists())
		{
			try
			{
				final Properties properties = loadProperties(file);
				return properties.getProperty(PROPERTY_VERSION);
			}
			catch(IOException e)
			{
				if(ApplicationManager.getApplication().isInternal())
				{
					LOGGER.warn(e);
				}
			}
		}
		return null;
	}

	public static Properties loadProperties(File antJar) throws IOException
	{
		Properties properties = new Properties();

		VirtualFile fileByIoFile = LocalFileSystem.getInstance().findFileByIoFile(antJar);
		if(fileByIoFile == null)
		{
			return properties;
		}
		VirtualFile archiveRootForLocalFile = ArchiveVfsUtil.getArchiveRootForLocalFile(fileByIoFile);
		if(archiveRootForLocalFile == null)
		{
			return properties;
		}
		VirtualFile fileByRelativePath = archiveRootForLocalFile.findFileByRelativePath(VERSION_RESOURCE);
		if(fileByRelativePath == null)
		{
			return null;
		}
		properties.load(fileByRelativePath.getInputStream());

		return properties;
	}
}
