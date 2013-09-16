package org.consulo.ant;

import org.consulo.ant.core.AntBuildSystemProvider;
import org.consulo.java.platform.module.extension.JavaModuleExtension;
import org.consulo.module.extension.ModuleExtensionWithSdk;

/**
 * @author VISTALL
 * @since 16.09.13.
 */
public class JavaAntBuildSystemProvider extends AntBuildSystemProvider {
	@Override
	public Class<? extends ModuleExtensionWithSdk<?>> getLanguageModuleExtensionClass() {
		return JavaModuleExtension.class;
	}
}
