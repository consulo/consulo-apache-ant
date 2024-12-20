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
package com.intellij.lang.ant.config.impl.configuration;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.impl.AntClasspathEntry;
import com.intellij.lang.ant.config.impl.AntReference;
import com.intellij.lang.ant.config.impl.GlobalAntConfiguration;
import consulo.application.AllIcons;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkUtil;
import consulo.ide.impl.idea.ide.macro.MacrosDialog;
import consulo.ide.impl.idea.util.IconUtil;
import consulo.ide.ui.OrderEntryAppearanceService;
import consulo.logging.Logger;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awtUnsafe.TargetAWT;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class AntUIUtil
{
	private static final Logger LOG = Logger.getInstance("#com.intellij.ant.impl.configuration.AntUIUtil");

	private AntUIUtil()
	{
	}

	public static class AntInstallationRenderer extends ColoredListCellRenderer
	{

		public AntInstallationRenderer()
		{
		}

		@Override
		protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus)
		{
			Sdk ant = (Sdk) value;
			if(ant == null)
			{
				return;
			}
			customizeAnt(ant, this);
		}
	}

	public static class AntReferenceRenderer extends ColoredListCellRenderer
	{
		private final GlobalAntConfiguration myConfiguration;

		public AntReferenceRenderer(GlobalAntConfiguration configuration)
		{
			myConfiguration = configuration;
		}

		@Override
		protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus)
		{
			if(value == null)
			{
				return;
			}
			customizeReference((AntReference) value, this, myConfiguration);
		}
	}

	public static void customizeReference(AntReference antReference, SimpleColoredComponent component, GlobalAntConfiguration configuration)
	{
		Sdk antInstallation = antReference.find(configuration);
		if(antInstallation != null)
		{
			customizeAnt(antInstallation, component);
		}
		else
		{
			component.setIcon(AllIcons.Nodes.PpInvalid);
			component.append(antReference.getName(), SimpleTextAttributes.ERROR_ATTRIBUTES);
		}
	}

	public static void customizeAnt(Sdk sdk, SimpleColoredComponent component)
	{
		component.setIcon(SdkUtil.getIcon(sdk));
		String name = sdk.getName();
		component.append(name, consulo.ui.ex.SimpleTextAttributes.REGULAR_ATTRIBUTES);
		String versionString = sdk.getVersionString();
		if(name.indexOf(versionString) == -1)
		{
			component.append(" (" + versionString + ")", SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
		}
	}


	public static class ClasspathRenderer extends ColoredListCellRenderer
	{
		@Override
		protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus)
		{
			AntClasspathEntry entry = (AntClasspathEntry) value;
			entry.getAppearance().customize(this);
		}
	}

	public static class PropertyValueCellEditor extends AbstractTableCellEditor
	{
		private final consulo.ui.ex.awt.CellEditorComponentWithBrowseButton<JTextField> myComponent;

		public PropertyValueCellEditor()
		{
			myComponent = new CellEditorComponentWithBrowseButton<JTextField>(new TextFieldWithBrowseButton(), this);
			getChildComponent().setBorder(BorderFactory.createLineBorder(JBColor.black));

			JButton button = myComponent.getComponentWithButton().getButton();
			button.setIcon(TargetAWT.to(IconUtil.getAddIcon()));
			button.setToolTipText(AntBundle.message("ant.property.value.editor.insert.macro.tooltip.text"));
			button.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					MacrosDialog dialog = new MacrosDialog(getChildComponent());
					dialog.show();
					if(dialog.isOK() && dialog.getSelectedMacro() != null)
					{
						JTextField textField = getChildComponent();

						String macro = dialog.getSelectedMacro().getName();
						int position = textField.getCaretPosition();
						try
						{
							textField.getDocument().insertString(position, "$" + macro + "$", null);
							textField.setCaretPosition(position + macro.length() + 2);
						}
						catch(BadLocationException ex)
						{
							LOG.error(ex);
						}
						textField.requestFocus();
					}
				}
			});
		}

		@Override
		public Object getCellEditorValue()
		{
			return getChildComponent().getText();
		}

		protected void initializeEditor(Object value)
		{
			getChildComponent().setText((String) value);
		}

		private JTextField getChildComponent()
		{
			return myComponent.getChildComponent();
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column)
		{
			getChildComponent().setText((String) value);
			return myComponent;
		}
	}

	public static class JavaSdkdkRenderer extends ColoredListCellRenderer
	{
		private final boolean myInComboBox;
		private final String myAutoSelectSdkName;

		public JavaSdkdkRenderer(boolean inComboBox, String autoSelectSdkName)
		{
			myInComboBox = inComboBox;
			myAutoSelectSdkName = autoSelectSdkName != null ? autoSelectSdkName : "";
		}

		@Override
		protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus)
		{
			String jdkName = (String) value;
			if(jdkName == null || jdkName.length() == 0)
			{
				jdkName = "";
			}
			Sdk jdk = GlobalAntConfiguration.findJdk(jdkName);
			if(jdk == null)
			{
				if(myAutoSelectSdkName.length() > 0)
				{
					setIcon(AllIcons.General.Jdk);
					append("Auto-Selected: " + myAutoSelectSdkName,
							consulo.ui.ex.SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
				}
				else
				{
					setIcon(AllIcons.Nodes.PpInvalid);
					append("Sdk not set", consulo.ui.ex.SimpleTextAttributes.ERROR_ATTRIBUTES);
				}
			}
			else
			{
				OrderEntryAppearanceService.getInstance().forSdk(jdk, myInComboBox, selected, true).customize(this);
			}
		}
	}
}
