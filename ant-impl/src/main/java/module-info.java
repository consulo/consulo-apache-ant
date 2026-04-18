/**
 * @author VISTALL
 * @since 08/05/2023
 */
open module consulo.apache.ant {
  requires consulo.apache.ant.rt.common;

  requires consulo.util.nodep;
  requires build.serviceMessages;

  // platform APIs
  requires consulo.annotation;
  requires consulo.application.api;
  requires consulo.application.content.api;
  requires consulo.application.ui.api;
  requires consulo.base.icon.library;
  requires consulo.build.ui.api;
  requires consulo.code.editor.api;
  requires consulo.color.scheme.api;
  requires consulo.compiler.api;
  requires consulo.compiler.artifact.api;
  requires consulo.component.api;
  requires consulo.configurable.api;
  requires consulo.container.api;
  requires consulo.datacontext.api;
  requires consulo.disposer.api;
  requires consulo.document.api;
  requires consulo.execution.api;
  requires consulo.execution.test.api;
  requires consulo.file.chooser.api;
  requires consulo.file.editor.api;
  requires consulo.index.io;
  requires consulo.language.api;
  requires consulo.language.editor.api;
  requires consulo.language.editor.refactoring.api;
  requires consulo.local.history.api;
  requires consulo.localize.api;
  requires consulo.logging.api;
  requires consulo.module.api;
  requires consulo.navigation.api;
  requires consulo.path.macro.api;
  requires consulo.process.api;
  requires consulo.project.api;
  requires consulo.project.ui.api;
  requires consulo.proxy;
  requires consulo.ui.api;
  requires consulo.ui.ex.api;
  requires consulo.ui.ex.awt.api;
  requires consulo.util.collection;
  requires consulo.util.concurrent;
  requires consulo.util.dataholder;
  requires consulo.util.io;
  requires consulo.util.lang;
  requires consulo.util.xml.fast.reader;
  requires consulo.util.xml.serializer;
  requires consulo.virtual.file.system.api;

  // plugin APIs
  requires com.intellij.xml;
  requires com.intellij.xml.api;
  requires com.intellij.xml.dom.api;
  requires com.intellij.xml.editor.api;
  requires com.intellij.properties;
  requires consulo.java;
  requires consulo.java.execution.api;
  requires consulo.java.language.api;

  // TODO remove in future
  requires consulo.ide.api;
  requires consulo.ide.impl;
  requires java.desktop;
}
