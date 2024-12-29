package consulo.apache.ant.execution;

import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 08/05/2023
 */
public interface MessageProcessor {
  void onMessage(@Nullable ServiceMessage message, @Nonnull String text);
}
