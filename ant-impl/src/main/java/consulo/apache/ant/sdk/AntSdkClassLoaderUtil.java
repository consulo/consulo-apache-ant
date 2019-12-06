package consulo.apache.ant.sdk;

import com.intellij.lang.ant.config.impl.AntInstallationClassLoaderHolder;
import com.intellij.lang.ant.config.impl.ClassLoaderHolder;
import com.intellij.openapi.projectRoots.Sdk;
import consulo.util.dataholder.Key;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 08.08.2015
 */
public class AntSdkClassLoaderUtil
{
	public static final Key<ClassLoaderHolder> CLASS_LOADER_HOLDER_KEY = Key.create("ant.sdk.classloader");

	@Nonnull
	public static ClassLoader getClassLoader(@Nonnull Sdk sdk)
	{
		ClassLoaderHolder loaderHolder = sdk.getUserData(CLASS_LOADER_HOLDER_KEY);
		if(loaderHolder == null)
		{
			sdk.putUserData(CLASS_LOADER_HOLDER_KEY, loaderHolder = new AntInstallationClassLoaderHolder(sdk));
		}

		return loaderHolder.getClassloader();
	}
}
