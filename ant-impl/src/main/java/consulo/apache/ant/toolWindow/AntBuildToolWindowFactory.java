package consulo.apache.ant.toolWindow;

import com.intellij.lang.ant.config.explorer.AntExplorer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import consulo.disposer.Disposer;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 16.09.13.
 */
public class AntBuildToolWindowFactory implements ToolWindowFactory, DumbAware
{
	@Override
	public void createToolWindowContent(@Nonnull Project project, @Nonnull ToolWindow toolWindow)
	{
		AntExplorer explorer = new AntExplorer(project);
		final ContentManager contentManager = toolWindow.getContentManager();
		final Content content = contentManager.getFactory().createContent(explorer, null, false);
		contentManager.addContent(content);
		Disposer.register(project, explorer);
	}
}
