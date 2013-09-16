package org.consulo.ant.core.toolWindow;

import com.intellij.lang.ant.config.explorer.AntExplorer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.consulo.ant.core.AntBuildSystemProvider;

/**
 * @author VISTALL
 * @since 16.09.13.
 */
public abstract class BaseAntBuildToolWindowFactory implements ToolWindowFactory {
	@Override
	public void createToolWindowContent(Project project, ToolWindow toolWindow) {
		AntBuildSystemProvider extension = AntBuildSystemProvider.EP_NAME.findExtension(getBuildSystemClass());
		if(extension == null) {
			return;
		}

		AntExplorer explorer = new AntExplorer(project, extension);
		final ContentManager contentManager = toolWindow.getContentManager();
		final Content content = contentManager.getFactory().createContent(explorer, null, false);
		contentManager.addContent(content);
		Disposer.register(project, explorer);
	}

	public abstract Class<? extends AntBuildSystemProvider> getBuildSystemClass();
}
