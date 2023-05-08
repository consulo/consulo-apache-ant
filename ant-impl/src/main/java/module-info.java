/**
 * @author VISTALL
 * @since 08/05/2023
 */
open module consulo.apache.ant {
  requires consulo.ide.api;
  requires consulo.apache.ant.rt.common;

  requires consulo.util.nodep;

  requires com.intellij.xml;
  requires consulo.java;
  requires com.intellij.properties;

  // TODO remove in future
  requires consulo.ide.impl;
  requires java.desktop;
}