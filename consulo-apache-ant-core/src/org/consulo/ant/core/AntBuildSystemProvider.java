package org.consulo.ant.core;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.consulo.module.extension.ModuleExtensionWithSdk;

import javax.swing.*;

/**
 * @author VISTALL
 * @since 16.09.13.
 */
public abstract class AntBuildSystemProvider {
	public static final ExtensionPointName<AntBuildSystemProvider> EP_NAME = ExtensionPointName.create("org.consulo.ant.core.buildSystemProvider");

	public abstract Class<? extends ModuleExtensionWithSdk<?>> getLanguageModuleExtensionClass();

	public Icon getBuildFileIcon() {
		return XmlFileType.INSTANCE.getIcon();
	}
}
