package consulo.apache.ant.execution;

/**
 * @author VISTALL
 * @since 08/05/2023
 */
public interface OutputWatcher {
  boolean isStopped();

  void stopProcess();

  boolean isTerminateInvoked();

  void setStopped(boolean stopped);

  int getErrorsCount();
}
