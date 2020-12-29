package org.kiwiproject.dropwizard.util.startup.listener;

import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.kiwiproject.dropwizard.util.startup.SystemExecutioner;

/**
 * A Jetty {@link org.eclipse.jetty.util.component.LifeCycle.Listener} that shuts down the system in the case of
 * a lifecycle failure.
 */
@Slf4j
public class StartupJettyLifeCycleListener extends AbstractLifeCycle.AbstractLifeCycleListener {

    private final SystemExecutioner executioner;

    public StartupJettyLifeCycleListener(SystemExecutioner executioner) {
        this.executioner = requireNotNull(executioner);
    }

    @Override
    public void lifeCycleFailure(LifeCycle event, Throwable cause) {
        LOG.error("Jetty LifeCycleFailure with event [{}]. Exiting the JVM!", event, cause);
        executioner.exit();
    }
}
