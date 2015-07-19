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

import javax.swing.Icon;

import org.jetbrains.annotations.NonNls;
import org.mustbe.consulo.apache.ant.ApacheAntIcons;
import com.intellij.icons.AllIcons;

/**
 * @author dyoma
 */
public enum AntElementRole
{
	TARGET_ROLE(AntBundle.message("ant.role.ant.target"), ApacheAntIcons.Target),
	PROPERTY_ROLE(AntBundle.message("ant.role.ant.property"), AllIcons.Nodes.Property),
	TASK_ROLE(AntBundle.message("ant.role.ant.task"), ApacheAntIcons.Task),
	USER_TASK_ROLE(AntBundle.message("ant.element.role.user.task"), ApacheAntIcons.Task),
	PROJECT_ROLE(AntBundle.message("ant.element.role.ant.project.name"), AllIcons.Nodes.Property),
	MACRODEF_ROLE(AntBundle.message("ant.element.role.macrodef.element"), ApacheAntIcons.Task),
	SCRIPTDEF_ROLE(AntBundle.message("ant.element.role.scriptdef.element"), ApacheAntIcons.Task),
	@NonNls NULL_ROLE("Ant element", null);

	AntElementRole(String name, Icon icon)
	{
		myName = name;
		myIcon = icon;
	}

	private final String myName;
	private final Icon myIcon;

	public String getName()
	{
		return myName;
	}

	public Icon getIcon()
	{
		return myIcon;
	}
}
