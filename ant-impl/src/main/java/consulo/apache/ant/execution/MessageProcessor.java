package consulo.apache.ant.execution;

import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 08/05/2023
 */
public interface MessageProcessor {
  void onMessage(@Nullable ServiceMessage message, @Nonnull String text);
}
