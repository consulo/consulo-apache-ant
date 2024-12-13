package consulo.apache.ant;

import consulo.annotation.DeprecationInfo;
import consulo.apache.ant.impl.icon.ApacheAntImplIconGroup;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;

@Deprecated
@DeprecationInfo("Use ApacheAntImplIconGroup")
public interface ApacheAntIcons {
  Image AntBuildXml = ApacheAntImplIconGroup.antbuildxml();
  Image AntGroup = PlatformIconGroup.nodesFolder();
  Image AntInstallation = ApacheAntImplIconGroup.antinstallation();
  Image MetaTarget = ApacheAntImplIconGroup.metatarget();
  Image Target = ApacheAntImplIconGroup.target();
  Image Task = PlatformIconGroup.nodesTask();
  Image ToolWindowAnt = ApacheAntImplIconGroup.toolwindowant();
  Image Verbose = PlatformIconGroup.filetypesText();
}