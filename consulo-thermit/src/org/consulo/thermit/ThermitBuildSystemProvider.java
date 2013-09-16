package org.consulo.thermit;

import org.consulo.ant.core.AntBuildSystemProvider;
import org.consulo.module.extension.ModuleExtensionWithSdk;
import org.napile.idea.plugin.module.extension.NapileModuleExtension;

/**
 * @author VISTALL
 * @since 16.09.13.
 */
public class ThermitBuildSystemProvider extends AntBuildSystemProvider {
	@Override
	public Class<? extends ModuleExtensionWithSdk<?>> getLanguageModuleExtensionClass() {
		return NapileModuleExtension.class;
	}
}
