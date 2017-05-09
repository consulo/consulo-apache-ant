package consulo.apache.ant.compiler;

import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;

/**
 * @author VISTALL
 * @since 09-May-17
 */
public class BeforeAntCompileTask implements CompileTask
{
	@Override
	public boolean execute(CompileContext compileContext)
	{
		final DataContext dataContext = SimpleDataContext.getProjectContext(compileContext.getProject());

		final AntConfiguration config = AntConfiguration.getInstance(compileContext.getProject());
		((AntConfigurationBase)config).ensureInitialized();
		return config.executeTargetBeforeCompile(dataContext);
	}
}
