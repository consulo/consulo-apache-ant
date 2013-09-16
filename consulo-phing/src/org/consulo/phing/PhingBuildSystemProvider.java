package org.consulo.phing;

import org.consulo.ant.core.AntBuildSystemProvider;
import org.consulo.module.extension.ModuleExtensionWithSdk;
import org.consulo.php.module.extension.PhpModuleExtension;

/**
 * @author VISTALL
 * @since 16.09.13.
 */
public class PhingBuildSystemProvider extends AntBuildSystemProvider {
	@Override
	public Class<? extends ModuleExtensionWithSdk<?>> getLanguageModuleExtensionClass() {
		return PhpModuleExtension.class;
	}
}
