package org.consulo.thermit.toolWindow;

import org.consulo.ant.core.AntBuildSystemProvider;
import org.consulo.ant.core.toolWindow.BaseAntBuildToolWindowFactory;
import org.consulo.thermit.ThermitBuildSystemProvider;

/**
 * @author VISTALL
 * @since 16.09.13.
 */
public class ThermitBuildToolWindowFactory extends BaseAntBuildToolWindowFactory {
	@Override
	public Class<? extends AntBuildSystemProvider> getBuildSystemClass() {
		return ThermitBuildSystemProvider.class;
	}
}
