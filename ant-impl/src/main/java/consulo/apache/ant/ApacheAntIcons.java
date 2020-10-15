package consulo.apache.ant;

import consulo.annotation.DeprecationInfo;
import consulo.apache.ant.impl.icon.ApacheAntImplIconGroup;
import consulo.ui.image.Image;

@Deprecated
@DeprecationInfo("Use ApacheAntImplIconGroup")
public interface ApacheAntIcons
{
	Image AntBuildXml = ApacheAntImplIconGroup.antBuildXml();
	Image AntGroup = ApacheAntImplIconGroup.antGroup();
	Image AntInstallation = ApacheAntImplIconGroup.antInstallation();
	Image ChangeView = ApacheAntImplIconGroup.changeView();
	Image Message = ApacheAntImplIconGroup.message();
	Image MetaTarget = ApacheAntImplIconGroup.metaTarget();
	Image Properties = ApacheAntImplIconGroup.properties();
	Image Target = ApacheAntImplIconGroup.target();
	Image Task = ApacheAntImplIconGroup.task();
	Image ToolWindowAnt = ApacheAntImplIconGroup.toolWindowAnt();
	Image Verbose = ApacheAntImplIconGroup.verbose();
}