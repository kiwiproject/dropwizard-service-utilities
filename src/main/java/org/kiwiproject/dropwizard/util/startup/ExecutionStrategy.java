package org.kiwiproject.dropwizard.util.startup;

import org.kiwiproject.base.KiwiDeprecated;

/**
 * Defines a strategy used in {@link SystemExecutioner} to terminate the JVM.
 *
 * @deprecated This class is moving to kiwi as {@code org.kiwiproject.base.system.ExecutionStrategy}
 * in kiwi 5.4.0 and will be removed from this library in 6.0.0. No action is required yet — this
 * is an advance warning. Note that the kiwi version declares only {@code exit(int exitCode)},
 * requiring an explicit exit code rather than relying on a default.
 */
@Deprecated(since = "5.3.0", forRemoval = true)
@KiwiDeprecated(
        since = "5.3.0",
        removeAt = "6.0.0",
        replacedBy = "org.kiwiproject.base.system.ExecutionStrategy",
        reference = "#673"
)
@SuppressWarnings({ "java:S1133", "removal", "DeprecatedIsStillUsed" })
public interface ExecutionStrategy {

    /**
     * Performs the exit operation.
     */
    void exit();
}
