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
package org.mustbe.consulo.apache.ant.sdk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.swing.Icon;

import org.consulo.lombok.annotations.LazyInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mustbe.consulo.apache.ant.ApacheAntIcons;
import org.mustbe.consulo.apache.ant.util.AntVersionUtil;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.types.BinariesOrderRootType;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.util.ArchiveVfsUtil;

/**
 * @author VISTALL
 * @since 15:52/19.05.13
 */
public class AntSdkType extends SdkType
{
	@NotNull
	@LazyInstance
	public static AntSdkType getInstance()
	{
		return EP_NAME.findExtension(AntSdkType.class);
	}

	public AntSdkType()
	{
		super("APACHE_ANT");
	}

	@NotNull
	@Override
	public Collection<String> suggestHomePaths()
	{
		PluginClassLoader classLoader = (PluginClassLoader) AntSdkType.class.getClassLoader();
		IdeaPluginDescriptor plugin = PluginManager.getPlugin(classLoader.getPluginId());
		assert plugin != null;
		VirtualFile fileByIoFile = LocalFileSystem.getInstance().findFileByIoFile(plugin.getPath());
		if(fileByIoFile == null)
		{
			return Collections.emptyList();
		}
		VirtualFile dist = fileByIoFile.findChild("dist");
		if(dist == null)
		{
			return Collections.emptyList();
		}
		VirtualFile[] children = dist.getChildren();
		List<String> list = new ArrayList<String>(children.length);
		for(VirtualFile child : children)
		{
			list.add(child.getPath());
		}
		return list;
	}

	@Override
	public boolean canCreatePredefinedSdks()
	{
		return true;
	}

	@Override
	public void setupSdkPaths(Sdk sdk)
	{
		SdkModificator sdkModificator = sdk.getSdkModificator();
		VirtualFile homeDirectory = sdk.getHomeDirectory();
		VirtualFile lib = homeDirectory.findChild("lib");
		if(lib != null)
		{
			for(VirtualFile virtualFile : lib.getChildren())
			{
				VirtualFile archiveRootForLocalFile = ArchiveVfsUtil.getArchiveRootForLocalFile(virtualFile);
				if(archiveRootForLocalFile != null)
				{
					sdkModificator.addRoot(archiveRootForLocalFile, BinariesOrderRootType.getInstance());
				}
			}
		}
		sdkModificator.commitChanges();
	}

	@Override
	public Icon getIcon()
	{
		return ApacheAntIcons.AntInstallation;
	}

	@Nullable
	@Override
	public Icon getGroupIcon()
	{
		return ApacheAntIcons.AntGroup;
	}

	@Override
	public boolean isValidSdkHome(String path)
	{
		return AntVersionUtil.getVersion(path) != null;
	}

	@Nullable
	@Override
	public String getVersionString(String sdkHome)
	{
		final String version = AntVersionUtil.getVersion(sdkHome);

		return version == null ? "0.0" : version;
	}

	@Override
	public String suggestSdkName(String currentSdkName, String sdkHome)
	{
		return getPresentableName() + " " + getVersionString(sdkHome);
	}

	@Override
	public boolean isRootTypeApplicable(OrderRootType type)
	{
		return type == BinariesOrderRootType.getInstance();
	}

	@NotNull
	@Override
	public String getPresentableName()
	{
		return "Apache Ant";
	}
}
