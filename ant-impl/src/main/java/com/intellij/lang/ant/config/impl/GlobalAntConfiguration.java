/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntConfigurationBase;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.apache.ant.sdk.AntSdkType;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.component.util.config.AbstractProperty;
import consulo.component.util.config.ExternalizablePropertyContainer;
import consulo.component.util.config.StorageProperty;
import consulo.component.util.config.ValueProperty;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTable;
import consulo.ide.ServiceManager;
import consulo.logging.Logger;
import consulo.pathMacro.MacroManager;
import consulo.project.Project;
import jakarta.inject.Singleton;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@State(name = "GlobalAntConfiguration", storages = @Storage(file = StoragePathMacros.APP_CONFIG + "/ant.xml"))
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
@Singleton
public class GlobalAntConfiguration implements PersistentStateComponent<Element> {
  @Nonnull
  public static GlobalAntConfiguration getInstance() {
    return ServiceManager.getService(GlobalAntConfiguration.class);
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.config.impl.AntGlobalConfiguration");
  public static final StorageProperty FILTERS_TABLE_LAYOUT = new StorageProperty("filtersTableLayout");
  public static final StorageProperty PROPERTIES_TABLE_LAYOUT = new StorageProperty("propertiesTableLayout");

  private final ExternalizablePropertyContainer myProperties = new ExternalizablePropertyContainer();
  public static final String BUNDLED_ANT_NAME = AntBundle.message("ant.reference.bundled.ant.name");

  public static final AbstractProperty<GlobalAntConfiguration> INSTANCE = new ValueProperty<>("$GlobalAntConfiguration.INSTANCE", null);
  @NonNls
  public static final String ANT_FILE = "ant";
  @NonNls
  public static final String LIB_DIR = "lib";
  @NonNls
  public static final String ANT_JAR_FILE_NAME = "ant.jar";

  public GlobalAntConfiguration() {
    myProperties.registerProperty(FILTERS_TABLE_LAYOUT);
    myProperties.registerProperty(PROPERTIES_TABLE_LAYOUT);
    INSTANCE.set(myProperties, this);
    myProperties.rememberKey(INSTANCE);
  }

  @Nullable
  @Override
  public Element getState() {
    Element element = new Element("state");
    myProperties.writeExternal(element);
    return element;
  }

  @Override
  public void loadState(Element state) {
    myProperties.readExternal(state);
  }

  @Nullable
  public Sdk findBundleAntBundle() {
    return SdkTable.getInstance().findPredefinedSdkByType(AntSdkType.getInstance());
  }

  @Nonnull
  public Map<AntReference, Sdk> getConfiguredAnts() {
    List<Sdk> sdksOfType = SdkTable.getInstance().getSdksOfType(AntSdkType.getInstance());
    Map<AntReference, Sdk> map = new LinkedHashMap<AntReference, Sdk>();
    for (Sdk sdk : sdksOfType) {
      if (sdk.isPredefined()) {
        map.put(AntReference.BUNDLED_ANT, sdk);
      }
      else {
        map.put(new AntReference.BindedReference(sdk), sdk);
      }
    }
    return map;
  }

  public AbstractProperty.AbstractPropertyContainer getProperties() {
    return myProperties;
  }

  public AbstractProperty.AbstractPropertyContainer getProperties(Project project) {
    return new CompositePropertyContainer(new AbstractProperty.AbstractPropertyContainer[]{
      myProperties,
      AntConfigurationBase.getInstance(project).getProperties()
    });
  }

  public static Sdk findJdk(final String jdkName) {
    return SdkTable.getInstance().findSdk(jdkName);
  }

  public static MacroManager getMacroManager() {
    return MacroManager.getInstance();
  }
}
