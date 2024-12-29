package consulo.apache.ant.toolWindow;

import com.intellij.lang.ant.config.explorer.AntExplorer;
import consulo.annotation.component.ExtensionImpl;
import consulo.apache.ant.impl.icon.ApacheAntImplIconGroup;
import consulo.application.dumb.DumbAware;
import consulo.disposer.Disposer;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.localize.LocalizeValue;
import consulo.module.extension.ModuleExtensionHelper;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowFactory;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.content.ContentManager;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.ui.ex.toolWindow.ToolWindowAnchor;
import consulo.ui.image.Image;

import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 16.09.13.
 */
@ExtensionImpl
public class AntBuildToolWindowFactory implements ToolWindowFactory, DumbAware {
  @Nonnull
  @Override
  public String getId() {
    return "Apache Ant";
  }

  @RequiredUIAccess
  @Override
  public void createToolWindowContent(@Nonnull Project project, @Nonnull ToolWindow toolWindow) {
    AntExplorer explorer = new AntExplorer(project);
    final ContentManager contentManager = toolWindow.getContentManager();
    final Content content = contentManager.getFactory().createContent(explorer, null, false);
    contentManager.addContent(content);
    Disposer.register(project, explorer);
  }

  @Nonnull
  @Override
  public ToolWindowAnchor getAnchor() {
    return ToolWindowAnchor.RIGHT;
  }

  @Nonnull
  @Override
  public Image getIcon() {
    return ApacheAntImplIconGroup.toolwindowant();
  }

  @Nonnull
  @Override
  public LocalizeValue getDisplayName() {
    return LocalizeValue.localizeTODO("Apache Ant");
  }

  @Override
  public boolean validate(@Nonnull Project project) {
    return ModuleExtensionHelper.getInstance(project).hasModuleExtension(JavaModuleExtension.class);
  }
}
