package com.intellij.lang.ant.config.execution;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;

import javax.annotation.Nullable;

@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class AntMessageCustomizer {

  public static final ExtensionPointName<AntMessageCustomizer> EP_NAME = ExtensionPointName.create(AntMessageCustomizer.class);

  @Nullable
  public AntMessage createCustomizedMessage(String text, int priority) {
    return null;
  }
}
