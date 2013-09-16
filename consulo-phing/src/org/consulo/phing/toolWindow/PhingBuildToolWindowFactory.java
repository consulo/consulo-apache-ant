package org.consulo.phing.toolWindow;

import org.consulo.ant.core.AntBuildSystemProvider;
import org.consulo.ant.core.toolWindow.BaseAntBuildToolWindowFactory;
import org.consulo.phing.PhingBuildSystemProvider;

/**
 * @author VISTALL
 * @since 16.09.13.
 */
public class PhingBuildToolWindowFactory extends BaseAntBuildToolWindowFactory {
	@Override
	public Class<? extends AntBuildSystemProvider> getBuildSystemClass() {
		return PhingBuildSystemProvider.class;
	}
}
