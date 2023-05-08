package consulo.apache.ant.rt;

import com.intellij.rt.ant.execution.AntMain2;
import jetbrains.buildServer.messages.serviceMessages.*;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.DefaultLogger;

/**
 * @author VISTALL
 * @since 08/05/2023
 */
public class ConsuloAntLogger extends DefaultLogger {
  @Override
  public void buildStarted(BuildEvent event) {
    AntMain2.OUT.println(new BuildStatus(String.valueOf(event.getPriority()), "buildStarted"));
  }

  @Override
  public void buildFinished(BuildEvent event) {
    AntMain2.OUT.println(new BuildStatus(String.valueOf(event.getPriority()), "buildFinished"));
  }

  @Override
  public void targetStarted(BuildEvent event) {
    AntMain2.OUT.println(new ProgressStart(event.getTarget().getName()));
  }

  @Override
  public void targetFinished(BuildEvent event) {
    AntMain2.OUT.println(new ProgressFinish(event.getTarget().getName()));
  }

  @Override
  public void taskStarted(BuildEvent event) {
    AntMain2.OUT.println(new TestStarted(event.getTask().getTaskName(), false, null));
  }

  @Override
  public void taskFinished(BuildEvent event) {
    AntMain2.OUT.println(new TestFinished(event.getTask().getTaskName(), 0));
  }

  @Override
  public void messageLogged(BuildEvent event) {
    AntMain2.OUT.println(new Message(event.getMessage(), String.valueOf(event.getPriority()), null));
  }
}
