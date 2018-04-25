package com.intellij.lang.ant.config.execution;

import javax.annotation.Nullable;

import com.intellij.openapi.extensions.ExtensionPointName;

public abstract class AntMessageCustomizer {

  public static final ExtensionPointName<AntMessageCustomizer> EP_NAME = ExtensionPointName.create("consulo.apache.ant.messageCustomizer");

  @Nullable
  public AntMessage createCustomizedMessage(String text, int priority) {
    return null;
  }
}
