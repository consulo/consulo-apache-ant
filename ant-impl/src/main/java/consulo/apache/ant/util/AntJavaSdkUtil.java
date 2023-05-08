package consulo.apache.ant.util;

import com.intellij.java.language.projectRoots.JavaSdk;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTable;

/**
 * @author VISTALL
 * @since 19.07.2015
 */
public class AntJavaSdkUtil {
  public static Sdk getBundleSdk() {
    return SdkTable.getInstance().findPredefinedSdkByType(JavaSdk.getInstance());
  }

  public static String getBundleSdkName() {
    Sdk bundleSdk = getBundleSdk();
    if (bundleSdk == null) {
      return null;
    }
    return bundleSdk.getName();
  }
}
