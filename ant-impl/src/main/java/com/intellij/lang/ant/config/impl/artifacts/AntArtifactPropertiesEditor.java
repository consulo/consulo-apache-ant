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

import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildModel;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.impl.BuildFileProperty;
import consulo.apache.ant.impl.localize.ApacheAntImplLocalize;
import consulo.compiler.artifact.ui.ArtifactEditorContext;
import consulo.compiler.artifact.ui.ArtifactPropertiesEditor;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.ui.Button;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.Table;
import consulo.ui.TableItemEditor;
import consulo.ui.TextAttribute;
import consulo.ui.TextBox;
import consulo.ui.UIAccess;
import consulo.ui.ValueComponent;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionToolbarPosition;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.dialog.ChooseItemsDialogDescriptor;
import consulo.ui.ex.toolbar.AddAction;
import consulo.ui.ex.toolbar.DownMoveAction;
import consulo.ui.ex.toolbar.EditAction;
import consulo.ui.ex.toolbar.RemoveAction;
import consulo.ui.ex.toolbar.ToolbarDecoratorBuilderFactory;
import consulo.ui.ex.toolbar.UpMoveAction;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * @author nik
 */
public class AntArtifactPropertiesEditor extends ArtifactPropertiesEditor {
    private final AntArtifactProperties myProperties;
    private final ArtifactEditorContext myContext;
    private final boolean myPostProcessing;
    private final MutableFlatDataModel<BuildFileProperty> myPropertiesModel = FlatDataModel.of(List.of());

    private @Nullable CheckBox myRunTargetCheckBox;
    private @Nullable Button mySelectTargetButton;
    private @Nullable Component myPropertiesPanel;
    private @Nullable AntBuildTarget myTarget;

    public AntArtifactPropertiesEditor(AntArtifactProperties properties, ArtifactEditorContext context, boolean postProcessing) {
        myProperties = properties;
        myContext = context;
        myPostProcessing = postProcessing;
    }

    @Override
    public LocalizeValue getTabName() {
        return myPostProcessing ? POST_PROCESSING_TAB : PRE_PROCESSING_TAB;
    }

    @Override
    @RequiredUIAccess
    public Component createUIComponent(Disposable uiDisposable) {
        CheckBox runTargetCheckBox = CheckBox.create(ApacheAntImplLocalize.artifactRunTargetNoneCheckbox());
        Button selectTargetButton = Button.create(ApacheAntImplLocalize.artifactSelectTargetButton(), event -> selectTarget());
        runTargetCheckBox.addValueListener(event -> {
            boolean selected = Boolean.TRUE.equals(event.getValue());
            selectTargetButton.setEnabled(selected);
            if (selected && myTarget == null) {
                selectTarget();
            }
            updatePanel();
        });

        Table<BuildFileProperty> table = Table.create(myPropertiesModel);
        addColumn(table, ApacheAntImplLocalize.editAntPropertiesNameColumnName(), BuildFileProperty::getPropertyName, BuildFileProperty::setPropertyName);
        addColumn(table, ApacheAntImplLocalize.editAntPropertiesValueColumnName(), BuildFileProperty::getPropertyValue, BuildFileProperty::setPropertyValue);

        Component propertiesPanel = ToolbarDecoratorBuilderFactory.getInstance()
            .create(table)
            .addOrReplaceAction(new AddPropertyAction(table))
            .addOrReplaceAction(new RemovePropertyAction())
            .disableAction(EditAction.class)
            .disableAction(UpMoveAction.class)
            .disableAction(DownMoveAction.class)
            .withToolbarPosition(ActionToolbarPosition.TOP)
            .build();

        HorizontalLayout targetPanel = HorizontalLayout.create();
        targetPanel.add(runTargetCheckBox);
        targetPanel.add(selectTargetButton);

        DockLayout panel = DockLayout.create();
        panel.top(targetPanel);
        panel.center(propertiesPanel);

        myRunTargetCheckBox = runTargetCheckBox;
        mySelectTargetButton = selectTargetButton;
        myPropertiesPanel = propertiesPanel;
        return panel;
    }

    @RequiredUIAccess
    private void addColumn(
        Table<BuildFileProperty> table,
        LocalizeValue header,
        Function<BuildFileProperty, @Nullable String> getter,
        BiConsumer<BuildFileProperty, String> setter
    ) {
        table.addColumn(header, property -> StringUtil.notNullize(getter.apply(property)))
            .setEditor(new TableItemEditor<>() {
                @Override
                @RequiredUIAccess
                public ValueComponent<String> createComponent(BuildFileProperty property) {
                    return TextBox.create(StringUtil.notNullize(getter.apply(property)));
                }

                @Override
                @RequiredUIAccess
                public void commit(BuildFileProperty property, @Nullable String value) {
                    setter.accept(property, StringUtil.notNullize(value));
                    myPropertiesModel.update(property);
                }

                @Override
                public boolean isEditable(BuildFileProperty property) {
                    return !AntArtifactProperties.isPredefinedProperty(property.getPropertyName());
                }
            });
    }

    @RequiredUIAccess
    private void selectTarget() {
        AntConfiguration antConfiguration = AntConfiguration.getInstance(myContext.getProject());
        List<AntBuildTarget> targets = new ArrayList<>();
        for (AntBuildFile buildFile : antConfiguration.getBuildFiles()) {
            collectTargets(targets, antConfiguration.getMetaTargets(buildFile));
            AntBuildModel model = antConfiguration.getModel(buildFile);
            if (model != null) {
                collectTargets(targets, model.getTargets());
            }
        }

        AntBuildTarget currentTarget = myTarget;
        UIAccess uiAccess = UIAccess.current();
        ChooseItemsDialogDescriptor.showAsync(
            myPropertiesPanel,
            ApacheAntImplLocalize.antTargetChoserTitle(),
            LocalizeValue.empty(),
            targets,
            (target, presentation) -> {
                presentation.append(target.getDisplayName());
                AntBuildFile buildFile = target.getModel().getBuildFile();
                if (buildFile != null) {
                    presentation.append(" (" + buildFile.getPresentableName() + ")", TextAttribute.GRAY);
                }
            },
            false,
            currentTarget == null ? List.of() : List.of(currentTarget)
        ).whenCompleteAsync((selected, error) -> {
            if (error == null && !selected.isEmpty()) {
                myTarget = selected.get(0);
                updatePanel();
            }
        }, uiAccess);
    }

    private static void collectTargets(List<AntBuildTarget> targets, AntBuildTarget[] candidates) {
        for (AntBuildTarget target : candidates) {
            if (target.getName() != null) {
                targets.add(target);
            }
        }
    }

    @RequiredUIAccess
    private void updatePanel() {
        CheckBox runTargetCheckBox = myRunTargetCheckBox;
        if (runTargetCheckBox == null) {
            return;
        }

        AntBuildTarget target = myTarget;
        if (target != null) {
            runTargetCheckBox.setLabelText(ApacheAntImplLocalize.artifactRunTarget0Checkbox(target.getName()));
        }
        else {
            runTargetCheckBox.setLabelText(ApacheAntImplLocalize.artifactRunTargetNoneCheckbox());
        }

        Component propertiesPanel = myPropertiesPanel;
        if (propertiesPanel != null) {
            propertiesPanel.setEnabledRecursive(target != null && Boolean.TRUE.equals(runTargetCheckBox.getValue()));
        }
    }

    @Override
    @RequiredUIAccess
    public void apply() {
        myProperties.setEnabled(isRunTargetSelected());
        AntBuildTarget target = myTarget;
        if (target != null) {
            VirtualFile file = target.getModel().getBuildFile().getVirtualFile();
            if (file != null) {
                myProperties.setFileUrl(file.getUrl());
                myProperties.setTargetName(target.getName());
                myProperties.setUserProperties(getUserProperties());
                return;
            }
        }
        myProperties.setFileUrl(null);
        myProperties.setTargetName(null);
    }

    private boolean isRunTargetSelected() {
        CheckBox runTargetCheckBox = myRunTargetCheckBox;
        return runTargetCheckBox != null && Boolean.TRUE.equals(runTargetCheckBox.getValue());
    }

    private List<BuildFileProperty> getUserProperties() {
        List<BuildFileProperty> properties = new ArrayList<>();
        for (int i = 0; i < myPropertiesModel.getSize(); i++) {
            BuildFileProperty property = myPropertiesModel.get(i);
            if (!AntArtifactProperties.isPredefinedProperty(property.getPropertyName())) {
                properties.add(new BuildFileProperty(property.getPropertyName(), property.getPropertyValue()));
            }
        }
        return properties;
    }

    @Override
    @RequiredUIAccess
    public boolean isModified() {
        if (myRunTargetCheckBox == null) {
            return false;
        }
        if (myProperties.isEnabled() != isRunTargetSelected()) {
            return true;
        }

        AntBuildTarget target = myTarget;
        if (target == null) {
            return myProperties.getFileUrl() != null;
        }
        if (!Objects.equals(target.getName(), myProperties.getTargetName())) {
            return true;
        }

        VirtualFile file = target.getModel().getBuildFile().getVirtualFile();
        if (file != null && !Objects.equals(file.getUrl(), myProperties.getFileUrl())) {
            return true;
        }
        return !getUserProperties().equals(myProperties.getUserProperties());
    }

    @Override
    @RequiredUIAccess
    public void reset() {
        CheckBox runTargetCheckBox = myRunTargetCheckBox;
        if (runTargetCheckBox == null) {
            return;
        }

        myTarget = myProperties.findTarget(AntConfiguration.getInstance(myContext.getProject()));
        runTargetCheckBox.setValue(myProperties.isEnabled(), false);
        Button selectTargetButton = mySelectTargetButton;
        if (selectTargetButton != null) {
            selectTargetButton.setEnabled(myProperties.isEnabled());
        }

        List<BuildFileProperty> properties = new ArrayList<>();
        for (BuildFileProperty property : myProperties.getAllProperties(myContext.getArtifact())) {
            properties.add(new BuildFileProperty(property.getPropertyName(), property.getPropertyValue()));
        }
        myPropertiesModel.replaceAll(properties);
        updatePanel();
    }

    private class AddPropertyAction extends AddAction<BuildFileProperty> {
        private final Table<BuildFileProperty> myTable;

        private AddPropertyAction(Table<BuildFileProperty> table) {
            myTable = table;
        }

        @Override
        @RequiredUIAccess
        protected void doAdd(AnActionEvent e) {
            BuildFileProperty property = new BuildFileProperty("", "");
            myPropertiesModel.add(property);
            myTable.select(property);
        }
    }

    private class RemovePropertyAction extends RemoveAction<BuildFileProperty> {
        @Override
        @RequiredUIAccess
        protected void doRemove(BuildFileProperty value, AnActionEvent e) {
            if (!AntArtifactProperties.isPredefinedProperty(value.getPropertyName())) {
                myPropertiesModel.remove(value);
            }
        }
    }
}
