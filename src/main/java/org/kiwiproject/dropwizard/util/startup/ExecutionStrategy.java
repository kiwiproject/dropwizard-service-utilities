package org.kiwiproject.dropwizard.util.startup;

/**
 * Defines a strategy used in {@link SystemExecutioner} to terminate the JVM.
 */
public interface ExecutionStrategy {

    /**
     * Performs the exit operation.
     */
    void exit();
}
