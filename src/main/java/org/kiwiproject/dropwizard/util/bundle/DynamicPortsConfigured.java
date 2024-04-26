package org.kiwiproject.dropwizard.util.bundle;

/**
 * Defines how the {@link DynamicPortsBundle} gets a {@link DynamicPortsConfiguration}
 * from a Dropwizard Configuration.
 */
public interface DynamicPortsConfigured<C> {
    DynamicPortsConfiguration getDynamicPortsConfiguration(C configuration);
}
