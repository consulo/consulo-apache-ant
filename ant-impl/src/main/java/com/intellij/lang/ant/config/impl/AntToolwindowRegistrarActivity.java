package com.intellij.lang.ant.config.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.project.Project;
import consulo.project.startup.BackgroundStartupActivity;
import consulo.ui.UIAccess;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 2024-08-10
 */
@ExtensionImpl
public class AntToolwindowRegistrarActivity implements BackgroundStartupActivity, DumbAware {
  @Override
  public void runActivity(@Nonnull Project project, @Nonnull UIAccess uiAccess) {
    AntToolwindowRegistrar registrar = project.getInstance(AntToolwindowRegistrar.class);

    registrar.projectOpened();
  }
}
