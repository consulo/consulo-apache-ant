package consulo.apache.ant.util;

import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTable;

/**
 * @author VISTALL
 * @since 19.07.2015
 */
public class AntJavaSdkUtil
{
	public static Sdk getBundleSdk()
	{
		return SdkTable.getInstance().findPredefinedSdkByType(JavaSdk.getInstance());
	}

	public static String getBundleSdkName()
	{
		Sdk bundleSdk = getBundleSdk();
		if(bundleSdk == null)
		{
			return null;
		}
		return bundleSdk.getName();
	}
}
