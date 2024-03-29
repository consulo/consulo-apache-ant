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
package com.intellij.lang.ant.config.impl.artifacts;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.impl.BuildFileProperty;
import com.intellij.lang.ant.config.impl.TargetChooserDialog;
import com.intellij.lang.ant.config.impl.configuration.UIPropertyBinding;
import consulo.compiler.artifact.ui.ArtifactEditorContext;
import consulo.compiler.artifact.ui.ArtifactPropertiesEditor;
import consulo.component.util.config.ListProperty;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.table.JBTable;
import consulo.ui.ex.awt.table.ListTableModel;
import consulo.ui.ex.awt.util.TableUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.function.Condition;
import consulo.virtualFileSystem.VirtualFile;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class AntArtifactPropertiesEditor extends ArtifactPropertiesEditor {
  private static final ListProperty<BuildFileProperty> ANT_PROPERTIES = ListProperty.create("ant-properties");
  private static final ColumnInfo<BuildFileProperty, String> NAME_COLUMN =
    new consulo.ui.ex.awt.ColumnInfo<BuildFileProperty, String>(AntBundle.message("edit.ant.properties.name.column.name")) {
      public String valueOf(BuildFileProperty buildFileProperty) {
        return buildFileProperty.getPropertyName();
      }

      public boolean isCellEditable(BuildFileProperty buildFileProperty) {
        return USER_PROPERTY_CONDITION.value(buildFileProperty);
      }

      public void setValue(BuildFileProperty buildFileProperty, String name) {
        buildFileProperty.setPropertyName(name);
      }
    };
  private static final ColumnInfo<BuildFileProperty, String> VALUE_COLUMN =
    new ColumnInfo<BuildFileProperty, String>(AntBundle.message("edit.ant.properties.value.column.name")) {
      public boolean isCellEditable(BuildFileProperty buildFileProperty) {
        return USER_PROPERTY_CONDITION.value(buildFileProperty);
      }

      public String valueOf(BuildFileProperty buildFileProperty) {
        return buildFileProperty.getPropertyValue();
      }

      public void setValue(BuildFileProperty buildFileProperty, String value) {
        buildFileProperty.setPropertyValue(value);
      }
    };
  private static final ColumnInfo[] PROPERTY_COLUMNS = new consulo.ui.ex.awt.ColumnInfo[]{NAME_COLUMN, VALUE_COLUMN};
  private static final Condition<BuildFileProperty> USER_PROPERTY_CONDITION = new Condition<BuildFileProperty>() {
    @Override
    public boolean value(BuildFileProperty property) {
      return !AntArtifactProperties.isPredefinedProperty(property.getPropertyName());
    }
  };
  private final AntArtifactProperties myProperties;
  private final ArtifactEditorContext myContext;
  private JPanel myMainPanel;
  private JCheckBox myRunTargetCheckBox;
  private FixedSizeButton mySelectTargetButton;
  private JBTable myPropertiesTable;
  private JPanel myPropertiesPanel;
  private AntBuildTarget myTarget;
  private final boolean myPostProcessing;
  private UIPropertyBinding.TableListBinding<BuildFileProperty> myBinding;
  protected SinglePropertyContainer<ListProperty> myContainer;

  public AntArtifactPropertiesEditor(AntArtifactProperties properties,
                                     consulo.compiler.artifact.ui.ArtifactEditorContext context,
                                     boolean postProcessing) {
    myProperties = properties;
    myContext = context;
    myPostProcessing = postProcessing;
    mySelectTargetButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        selectTarget();
      }
    });
    myRunTargetCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        mySelectTargetButton.setEnabled(myRunTargetCheckBox.isSelected());
        if (myRunTargetCheckBox.isSelected() && myTarget == null) {
          selectTarget();
        }
        updatePanel();
      }
    });

    myPropertiesTable = new JBTable();
    UIPropertyBinding.Composite binding = new UIPropertyBinding.Composite();
    myBinding = binding.bindList(myPropertiesTable, PROPERTY_COLUMNS, ANT_PROPERTIES);
    myPropertiesPanel.add(
      ToolbarDecorator.createDecorator(myPropertiesTable)
                      .setAddAction(new AnActionButtonRunnable() {
                        @Override
                        public void run(AnActionButton button) {
                          consulo.ui.ex.awt.table.ListTableModel<BuildFileProperty> model =
                            (ListTableModel<BuildFileProperty>)myPropertiesTable.getModel();
                          if (myPropertiesTable.isEditing() && !myPropertiesTable.getCellEditor().stopCellEditing()) {
                            return;
                          }
                          BuildFileProperty item = new BuildFileProperty();
                          ArrayList<BuildFileProperty> items = new ArrayList<BuildFileProperty>(model.getItems());
                          items.add(item);
                          model.setItems(items);
                          int newIndex = model.indexOf(item);
                          ListSelectionModel selectionModel = myPropertiesTable.getSelectionModel();
                          selectionModel.clearSelection();
                          selectionModel.setSelectionInterval(newIndex, newIndex);
                          ColumnInfo[] columns = model.getColumnInfos();
                          for (int i = 0; i < columns.length; i++) {
                            consulo.ui.ex.awt.ColumnInfo column = columns[i];
                            if (column.isCellEditable(item)) {
                              myPropertiesTable.requestFocusInWindow();
                              myPropertiesTable.editCellAt(newIndex, i);
                              break;
                            }
                          }
                        }
                      }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          TableUtil.removeSelectedItems(myPropertiesTable);
        }
      }).setRemoveActionUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          final ListSelectionModel selectionModel = myPropertiesTable.getSelectionModel();
          consulo.ui.ex.awt.table.ListTableModel<BuildFileProperty> model = (ListTableModel<BuildFileProperty>)myPropertiesTable.getModel();
          boolean enable = false;
          if (!selectionModel.isSelectionEmpty()) {
            enable = true;
            for (int i : myPropertiesTable.getSelectedRows()) {
              if (AntArtifactProperties.isPredefinedProperty(model.getItems().get(i).getPropertyName())) {
                enable = false;
                break;
              }
            }
          }
          return enable;
        }
      }).disableUpDownActions().createPanel(), BorderLayout.CENTER);
  }

  private void selectTarget() {
    final TargetChooserDialog dialog = new TargetChooserDialog(myContext.getProject(), myTarget);
    dialog.show();
    if (dialog.isOK()) {
      myTarget = dialog.getSelectedTarget();
      updatePanel();
    }
  }

  private void updatePanel() {
    if (myTarget != null) {
      myRunTargetCheckBox.setText("Run Ant target '" + myTarget.getName() + "'");
    }
    else {
      myRunTargetCheckBox.setText("Run Ant target <none>");
    }
    final boolean enabled = myTarget != null && myRunTargetCheckBox.isSelected();
    UIUtil.setEnabled(myPropertiesPanel, enabled, true);
  }

  public String getTabName() {
    return myPostProcessing ? POST_PROCESSING_TAB : PRE_PROCESSING_TAB;
  }

  public void apply() {
    myProperties.setEnabled(myRunTargetCheckBox.isSelected());
    if (myTarget != null) {
      final consulo.virtualFileSystem.VirtualFile file = myTarget.getModel().getBuildFile().getVirtualFile();
      if (file != null) {
        myProperties.setFileUrl(file.getUrl());
        myProperties.setTargetName(myTarget.getName());
        final List<BuildFileProperty> properties = getUserProperties();
        myProperties.setUserProperties(properties);
        return;
      }
    }
    myProperties.setFileUrl(null);
    myProperties.setTargetName(null);
  }

  private List<BuildFileProperty> getUserProperties() {
    final SinglePropertyContainer<ListProperty> container = new SinglePropertyContainer<ListProperty>(ANT_PROPERTIES, null);
    myBinding.apply(container);
    final List<BuildFileProperty> allProperties = (List<BuildFileProperty>)container.getValueOf(ANT_PROPERTIES);
    return ContainerUtil.filter(allProperties, USER_PROPERTY_CONDITION);
  }

  public JComponent createComponent() {
    return myMainPanel;
  }

  public boolean isModified() {
    if (myProperties.isEnabled() != myRunTargetCheckBox.isSelected()) return true;
    if (myTarget == null) {
      return myProperties.getFileUrl() != null;
    }
    if (!Comparing.equal(myTarget.getName(), myProperties.getTargetName())) return true;

    final VirtualFile file = myTarget.getModel().getBuildFile().getVirtualFile();
    if (file != null && !Comparing.equal(file.getUrl(), myProperties.getFileUrl())) return true;

    return !getUserProperties().equals(myProperties.getUserProperties());
  }

  public void reset() {
    myRunTargetCheckBox.setSelected(myProperties.isEnabled());
    myTarget = myProperties.findTarget(AntConfiguration.getInstance(myContext.getProject()));
    final List<BuildFileProperty> properties = new ArrayList<BuildFileProperty>();
    for (BuildFileProperty property : myProperties.getAllProperties(myContext.getArtifact())) {
      properties.add(new BuildFileProperty(property.getPropertyName(), property.getPropertyValue()));
    }
    myContainer = new SinglePropertyContainer<ListProperty>(ANT_PROPERTIES, properties);
    myBinding.loadValues(myContainer);
    updatePanel();
  }

  public void disposeUIResources() {
  }
}
