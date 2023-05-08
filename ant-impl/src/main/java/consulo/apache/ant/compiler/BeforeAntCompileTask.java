package consulo.apache.ant.compiler;

import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.AntConfigurationBase;
import consulo.annotation.component.ExtensionImpl;
import consulo.compiler.BeforeCompileTask;
import consulo.compiler.CompileContext;
import consulo.dataContext.DataContext;
import consulo.ide.impl.idea.openapi.actionSystem.impl.SimpleDataContext;

/**
 * @author VISTALL
 * @since 09-May-17
 */
@ExtensionImpl
public class BeforeAntCompileTask implements BeforeCompileTask {
  @Override
  public boolean execute(CompileContext compileContext) {
    final DataContext dataContext = SimpleDataContext.getProjectContext(compileContext.getProject());

    final AntConfiguration config = AntConfiguration.getInstance(compileContext.getProject());
    ((AntConfigurationBase)config).ensureInitialized();
    return config.executeTargetBeforeCompile(dataContext);
  }
}
