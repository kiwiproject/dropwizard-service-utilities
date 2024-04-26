package org.kiwiproject.dropwizard.util.bundle;

/**
 * Defines how {@link StartupLockBundle} gets a {@link StartupLockConfiguration}
 * from a Dropwizard Configuration.
 */
public interface StartupLockConfigured<C> {
    StartupLockConfiguration getStartupLockConfiguration(C configuration);
}
