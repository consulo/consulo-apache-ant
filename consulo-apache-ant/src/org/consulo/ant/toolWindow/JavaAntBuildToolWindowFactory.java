package org.consulo.ant.toolWindow;

import org.consulo.ant.JavaAntBuildSystemProvider;
import org.consulo.ant.core.AntBuildSystemProvider;
import org.consulo.ant.core.toolWindow.BaseAntBuildToolWindowFactory;

/**
 * @author VISTALL
 * @since 16.09.13.
 */
public class JavaAntBuildToolWindowFactory extends BaseAntBuildToolWindowFactory {
	@Override
	public Class<? extends AntBuildSystemProvider> getBuildSystemClass() {
		return JavaAntBuildSystemProvider.class;
	}
}
