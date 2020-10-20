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

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.actions.TargetActionStub;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 24, 2007
 */
@Singleton
public class AntToolwindowRegistrar implements Disposable
{
	private final Project myProject;

	@jakarta.inject.Inject
	public AntToolwindowRegistrar(Project project, StartupManager startupManager)
	{
		myProject = project;
		if(project.isDefault())
		{
			return;
		}
		startupManager.registerPostStartupActivity(uiAccess -> projectOpened());
	}

	private void projectOpened()
	{
		final KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
		final String prefix = AntConfiguration.getActionIdPrefix(myProject);
		final ActionManager actionManager = ActionManager.getInstance();

		for(Keymap keymap : keymapManager.getAllKeymaps())
		{
			for(String id : keymap.getActionIds())
			{
				if(id.startsWith(prefix) && actionManager.getAction(id) == null)
				{
					actionManager.registerAction(id, new TargetActionStub(id, myProject));
				}
			}
		}
	}

	@Override
	public void dispose()
	{
		final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
		final String[] oldIds = actionManager.getActionIds(AntConfiguration.getActionIdPrefix(myProject));
		for(String oldId : oldIds)
		{
			actionManager.unregisterAction(oldId);
		}
	}
}
