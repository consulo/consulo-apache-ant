/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.impl.*;
import consulo.component.util.config.AbstractProperty;
import consulo.content.bundle.Sdk;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.execution.ui.awt.RawCommandLineEditor;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.project.Project;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.table.JBTable;
import consulo.ui.ex.awt.table.ListTableModel;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class BuildFilePropertiesPanel {
  @NonNls
  private static final String DIMENSION_SERVICE_KEY = "antBuildFilePropertiesDialogDimension";

  private final Form myForm;
  private AntBuildFileBase myBuildFile;

  private BuildFilePropertiesPanel(@Nonnull final Project project) {
    myForm = new Form(project);
  }

  private void reset(final AntBuildFileBase buildFile) {
    myBuildFile = buildFile;
    buildFile.updateProperties();
    myForm.reset(myBuildFile);
  }

  private void apply() {
    myForm.apply(myBuildFile);
    myBuildFile.updateConfig();
  }

  private boolean showDialog() {
    DialogBuilder builder = new DialogBuilder(myBuildFile.getProject());
    builder.setCenterPanel(myForm.myWholePanel);
    builder.setDimensionServiceKey(DIMENSION_SERVICE_KEY);
    builder.setPreferredFocusComponent(myForm.getPreferedFocusComponent());
    builder.setTitle(AntBundle.message("build.file.properties.dialog.title"));
    builder.removeAllActions();
    builder.addOkAction();
    builder.addCancelAction();
    builder.setHelpId("reference.dialogs.buildfileproperties");

    boolean isOk = builder.show() == DialogWrapper.OK_EXIT_CODE;
    if (isOk) {
      apply();
    }
    beforeClose();
    return isOk;
  }

  private void beforeClose() {
    myForm.beforeClose(myBuildFile);
    Disposer.dispose(myForm);
  }

  public static boolean editBuildFile(AntBuildFileBase buildFile, @Nonnull final Project project) {
    BuildFilePropertiesPanel panel = new BuildFilePropertiesPanel(project);
    panel.reset(buildFile);
    return panel.showDialog();
  }

  abstract static class Tab {
    private final UIPropertyBinding.Composite myBinding = new UIPropertyBinding.Composite();

    public abstract JComponent getComponent();

    public abstract String getDisplayName();

    public UIPropertyBinding.Composite getBinding() {
      return myBinding;
    }

    public void reset(AbstractProperty.AbstractPropertyContainer options) {
      myBinding.loadValues(options);
    }

    public void apply(AbstractProperty.AbstractPropertyContainer options) {
      myBinding.apply(options);
    }

    public void beforeClose(AbstractProperty.AbstractPropertyContainer options) {
      myBinding.beforeClose(options);
    }

    public abstract JComponent getPreferedFocusComponent();
  }

  private static class Form implements Disposable {
    private JLabel myBuildFileName;
    private JTextField myXmx;
    private JTextField myXss;
    private JCheckBox myRunInBackground;
    private JCheckBox myCloseOnNoError;
    private JPanel myTabsPlace;
    private JPanel myWholePanel;
    private JLabel myHeapSizeLabel;
    private final Tab[] myTabs;
    private final UIPropertyBinding.Composite myBinding = new UIPropertyBinding.Composite();
    private final TabbedPaneWrapper myWrapper;

    private Form(@Nonnull final Project project) {
      myTabs = new Tab[]{
        new PropertiesTab(),
        new ExecutionTab(GlobalAntConfiguration.getInstance(), project),
        new AdditionalClasspathTab(),
        new FiltersTab()
      };

      myHeapSizeLabel.setLabelFor(myXmx);
      myWrapper = new consulo.ui.ex.awt.TabbedPaneWrapper(this);
      myTabsPlace.setLayout(new BorderLayout());
      myTabsPlace.add(myWrapper.getComponent(), BorderLayout.CENTER);

      myBinding.bindBoolean(myRunInBackground, AntBuildFileImpl.RUN_IN_BACKGROUND);
      myBinding.bindBoolean(myCloseOnNoError, AntBuildFileImpl.CLOSE_ON_NO_ERRORS);
      myBinding.bindInt(myXmx, AntBuildFileImpl.MAX_HEAP_SIZE);
      myBinding.bindInt(myXss, AntBuildFileImpl.MAX_STACK_SIZE);

      for (Tab tab : myTabs) {
        myWrapper.addTab(tab.getDisplayName(), tab.getComponent());
      }
    }

    public JComponent getComponent() {
      return myWholePanel;
    }

    public JComponent getPreferedFocusComponent() {
      return myTabs[0].getPreferedFocusComponent();
    }

    public void reset(final AntBuildFileBase buildFile) {
      myBinding.loadValues(buildFile.getAllOptions());
      myBuildFileName.setText(buildFile.getPresentableUrl());
      for (Tab tab : myTabs) {
        tab.reset(buildFile.getAllOptions());
      }
    }

    public void apply(AntBuildFileBase buildFile) {
      myBinding.apply(buildFile.getAllOptions());
      for (Tab tab : myTabs) {
        tab.apply(buildFile.getAllOptions());
      }
    }

    public void beforeClose(AntBuildFileBase buildFile) {
      myBinding.beforeClose(buildFile.getAllOptions());
      for (Tab tab : myTabs) {
        tab.beforeClose(buildFile.getAllOptions());
      }
    }

    @Override
    public void dispose() {
    }
  }

  private static class PropertiesTab extends Tab {
    private JTable myPropertiesTable;
    private JPanel myWholePanel;

    private static final ColumnInfo<BuildFileProperty, String> NAME_COLUMN = new ColumnInfo<BuildFileProperty,
      String>(AntBundle.message("edit.ant.properties.name.column.name")) {
      @Override
      public String valueOf(BuildFileProperty buildFileProperty) {
        return buildFileProperty.getPropertyName();
      }

      @Override
      public boolean isCellEditable(BuildFileProperty buildFileProperty) {
        return true;
      }

      @Override
      public void setValue(BuildFileProperty buildFileProperty, String name) {
        buildFileProperty.setPropertyName(name);
      }
    };
    private static final ColumnInfo<BuildFileProperty, String> VALUE_COLUMN = new ColumnInfo<BuildFileProperty,
      String>(AntBundle.message("edit.ant.properties.value.column.name")) {
      @Override
      public boolean isCellEditable(BuildFileProperty buildFileProperty) {
        return true;
      }

      @Override
      public String valueOf(BuildFileProperty buildFileProperty) {
        return buildFileProperty.getPropertyValue();
      }

      @Override
      public void setValue(BuildFileProperty buildFileProperty, String value) {
        buildFileProperty.setPropertyValue(value);
      }

      @Override
      public TableCellEditor getEditor(BuildFileProperty item) {
        return new AntUIUtil.PropertyValueCellEditor();
      }
    };
    private static final ColumnInfo[] PROPERTY_COLUMNS = new ColumnInfo[]{
      NAME_COLUMN,
      VALUE_COLUMN
    };

    public PropertiesTab() {
      myPropertiesTable = new JBTable();
      UIPropertyBinding.TableListBinding<BuildFileProperty> tableListBinding = getBinding().bindList(myPropertiesTable, PROPERTY_COLUMNS,
                                                                                                     AntBuildFileImpl.ANT_PROPERTIES);
      tableListBinding.setColumnWidths(GlobalAntConfiguration.PROPERTIES_TABLE_LAYOUT);

      myWholePanel = ToolbarDecorator.createDecorator(myPropertiesTable).setAddAction(new AnActionButtonRunnable() {


        @Override
        public void run(AnActionButton button) {
          if (myPropertiesTable.isEditing() && !myPropertiesTable.getCellEditor().stopCellEditing()) {
            return;
          }
          BuildFileProperty item = new BuildFileProperty();
          consulo.ui.ex.awt.table.ListTableModel<BuildFileProperty> model = (ListTableModel<BuildFileProperty>)myPropertiesTable.getModel();
          ArrayList<BuildFileProperty> items = new ArrayList<BuildFileProperty>(model.getItems());
          items.add(item);
          model.setItems(items);
          int newIndex = model.indexOf(item);
          ListSelectionModel selectionModel = myPropertiesTable.getSelectionModel();
          selectionModel.clearSelection();
          selectionModel.setSelectionInterval(newIndex, newIndex);
          ColumnInfo[] columns = model.getColumnInfos();
          for (int i = 0; i < columns.length; i++) {
            ColumnInfo column = columns[i];
            if (column.isCellEditable(item)) {
              myPropertiesTable.requestFocusInWindow();
              myPropertiesTable.editCellAt(newIndex, i);
              break;
            }
          }
        }
      }).disableUpDownActions().createPanel();
      myWholePanel.setBorder(null);
    }

    @Override
    public JComponent getComponent() {
      return myWholePanel;
    }

    @Override
    @Nullable
    public String getDisplayName() {
      return AntBundle.message("edit.ant.properties.tab.display.name");
    }

    @Override
    public JComponent getPreferedFocusComponent() {
      return myPropertiesTable;
    }
  }

  private static class FiltersTab extends Tab {
    private JTable myFiltersTable;
    private JPanel myWholePanel;

    private static final int PREFERRED_CHECKBOX_COLUMN_WIDTH = new JCheckBox().getPreferredSize().width + 4;
    private static final ColumnInfo<TargetFilter, Boolean> CHECK_BOX_COLUMN = new ColumnInfo<TargetFilter, Boolean>("") {
      @Override
      public Boolean valueOf(TargetFilter targetFilter) {
        return targetFilter.isVisible();
      }

      @Override
      public void setValue(TargetFilter targetFilter, Boolean aBoolean) {
        targetFilter.setVisible(aBoolean.booleanValue());
      }

      @Override
      public int getWidth(JTable table) {
        return PREFERRED_CHECKBOX_COLUMN_WIDTH;
      }

      @Override
      public Class getColumnClass() {
        return Boolean.class;
      }

      @Override
      public boolean isCellEditable(TargetFilter targetFilter) {
        return true;
      }
    };

    private static final Comparator<TargetFilter> NAME_COMPARATOR = new Comparator<TargetFilter>() {
      @Override
      public int compare(TargetFilter o1, TargetFilter o2) {
        final String name1 = o1.getTargetName();
        if (name1 == null) {
          return -1;
        }
        final String name2 = o2.getTargetName();
        if (name2 == null) {
          return 1;
        }
        return name1.compareToIgnoreCase(name2);
      }
    };
    private static final ColumnInfo<TargetFilter, String> NAME_COLUMN =
      new ColumnInfo<TargetFilter, String>(AntBundle.message("ant.target")) {
        @Override
        public String valueOf(TargetFilter targetFilter) {
          return targetFilter.getTargetName();
        }

        @Override
        public Comparator<TargetFilter> getComparator() {
          return NAME_COMPARATOR;
        }
      };

    private static final Comparator<TargetFilter> DESCRIPTION_COMPARATOR = new Comparator<TargetFilter>() {
      @Override
      public int compare(TargetFilter o1, TargetFilter o2) {
        String description1 = o1.getDescription();
        if (description1 == null) {
          description1 = "";
        }
        String description2 = o2.getDescription();
        if (description2 == null) {
          description2 = "";
        }
        return description1.compareToIgnoreCase(description2);
      }
    };
    private static final ColumnInfo<TargetFilter, String> DESCRIPTION = new ColumnInfo<TargetFilter,
      String>(AntBundle.message("edit.ant.properties.description.column.name")) {
      @Override
      public String valueOf(TargetFilter targetFilter) {
        return targetFilter.getDescription();
      }

      @Override
      public Comparator<TargetFilter> getComparator() {
        return DESCRIPTION_COMPARATOR;
      }
    };
    private static final ColumnInfo[] COLUMNS = new ColumnInfo[]{
      CHECK_BOX_COLUMN,
      NAME_COLUMN,
      DESCRIPTION
    };

    public FiltersTab() {
      myFiltersTable.getTableHeader().setReorderingAllowed(false);

      UIPropertyBinding.TableListBinding tableListBinding = getBinding().bindList(myFiltersTable, COLUMNS, AntBuildFileImpl.TARGET_FILTERS);
      tableListBinding.setColumnWidths(GlobalAntConfiguration.FILTERS_TABLE_LAYOUT);
      tableListBinding.setSortable(true);
    }

    @Override
    public JComponent getComponent() {
      return myWholePanel;
    }

    @Override
    @Nullable
    public String getDisplayName() {
      return AntBundle.message("edit.ant.properties.filters.tab.display.name");
    }

    @Override
    public JComponent getPreferedFocusComponent() {
      return myFiltersTable;
    }

    @Override
    public void reset(AbstractProperty.AbstractPropertyContainer options) {
      super.reset(options);
    }
  }

  static class ExecutionTab extends Tab {
    private JPanel myWholePanel;
    private JLabel myAntCmdLineLabel;
    private JLabel myJDKLabel;
    private RawCommandLineEditor myAntCommandLine;
    private ComboboxWithBrowseButton myAnts;
    private ComboboxWithBrowseButton myJDKs;
    private final ChooseAndEditComboBoxController<Sdk, String> myJDKsController;
    private SimpleColoredComponent myDefaultAnt;
    private JRadioButton myUseCastomAnt;
    private JRadioButton myUseDefaultAnt;

    private AntReference myProjectDefaultAnt = null;
    private final GlobalAntConfiguration myAntGlobalConfiguration;

    public ExecutionTab(final GlobalAntConfiguration antConfiguration, @Nonnull final Project project) {
      myAntGlobalConfiguration = antConfiguration;
      myAntCommandLine.attachLabel(myAntCmdLineLabel);
      myAntCommandLine.setDialogCaption(AntBundle.message("run.execution.tab.ant.command.line.dialog.title"));
      setLabelFor(myJDKLabel, myJDKs);

      myJDKsController =
        new ChooseAndEditComboBoxController<Sdk, String>(myJDKs, new consulo.ide.impl.idea.util.containers.Convertor<Sdk, String>() {
          @Override
          public String convert(Sdk jdk) {
            return jdk != null ? jdk.getName() : "";
          }
        }, String.CASE_INSENSITIVE_ORDER) {
          @Override
          public Iterator<Sdk> getAllListItems() {
            List<Sdk> sdksOfType = JavaSdkTypeUtil.getAllJavaSdks();
            List<Sdk> controller = new ArrayList<>(sdksOfType);
            controller.add(0, null);
            return controller.iterator();
          }

          @Override
          public Sdk openConfigureDialog(Sdk jdk, JComponent parent) {
//            SingleSdkEditor editor = new SingleSdkEditor(jdk, myJDKs.getComboBox());
//            editor.show();
//            return editor.getSelectedSdk();
            // TODO
            return null;
          }
        };

      UIPropertyBinding.Composite binding = getBinding();
      binding.bindString(myAntCommandLine, AntBuildFileImpl.ANT_COMMAND_LINE_PARAMETERS);
      binding.bindString(myJDKs.getComboBox(), AntBuildFileImpl.CUSTOM_JDK_NAME);
      binding.addBinding(new RunWithAntBinding(myUseDefaultAnt, myUseCastomAnt, myAnts, myAntGlobalConfiguration));
    }

    @Override
    public JComponent getComponent() {
      return myWholePanel;
    }

    @Override
    @Nullable
    public String getDisplayName() {
      return AntBundle.message("edit.ant.properties.execution.tab.display.name");
    }

    @Override
    public void reset(AbstractProperty.AbstractPropertyContainer options) {
      String autoselectJdk = AntConfigurationImpl.DEFAULT_JDK_NAME.get(options);
      myJDKsController.setRenderer(new AntUIUtil.JavaSdkdkRenderer(true, autoselectJdk));
      super.reset(options);
      myJDKsController.resetList(null);
      myProjectDefaultAnt = AntConfigurationImpl.DEFAULT_ANT.get(options);
      updateDefaultAnt();
    }

    private void updateDefaultAnt() {
      myDefaultAnt.clear();
      AntUIUtil.customizeReference(myProjectDefaultAnt, myDefaultAnt, myAntGlobalConfiguration);
      myDefaultAnt.revalidate();
      myDefaultAnt.repaint();
    }

    @Override
    public void apply(AbstractProperty.AbstractPropertyContainer options) {
      AntConfigurationImpl.DEFAULT_ANT.set(options, myProjectDefaultAnt);
      super.apply(options);
    }

    @Override
    public JComponent getPreferedFocusComponent() {
      return myAntCommandLine.getTextField();
    }
  }

  private static class AdditionalClasspathTab extends Tab {
    private JPanel myWholePanel;
    private AntClasspathEditorPanel myClasspath;

    public AdditionalClasspathTab() {
      getBinding().addBinding(myClasspath.setClasspathProperty(AntBuildFileImpl.ADDITIONAL_CLASSPATH));
    }

    @Override
    public JComponent getComponent() {
      return myWholePanel;
    }

    @Override
    @Nullable
    public String getDisplayName() {
      return AntBundle.message("edit.ant.properties.additional.classpath.tab.display.name");
    }

    @Override
    public JComponent getPreferedFocusComponent() {
      return myClasspath.getPreferedFocusComponent();
    }
  }

  private static void setLabelFor(JLabel label, ComponentWithBrowseButton component) {
    label.setLabelFor(component.getChildComponent());
  }
}
