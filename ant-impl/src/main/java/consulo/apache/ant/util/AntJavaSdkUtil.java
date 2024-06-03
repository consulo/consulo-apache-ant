package consulo.apache.ant.util;

import consulo.content.bundle.Sdk;
import consulo.java.language.bundle.JavaSdkTypeUtil;

/**
 * @author VISTALL
 * @since 19.07.2015
 */
public class AntJavaSdkUtil {
  public static Sdk getBundleSdk() {
    return JavaSdkTypeUtil.getAllJavaSdks().stream().filter(Sdk::isPredefined).findAny().orElse(null);
  }

  public static String getBundleSdkName() {
    Sdk bundleSdk = getBundleSdk();
    if (bundleSdk == null) {
      return null;
    }
    return bundleSdk.getName();
  }
}
