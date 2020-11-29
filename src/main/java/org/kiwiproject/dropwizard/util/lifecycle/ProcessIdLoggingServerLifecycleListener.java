package org.kiwiproject.dropwizard.util.lifecycle;

import static java.util.Objects.nonNull;

import io.dropwizard.lifecycle.ServerLifecycleListener;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Server;

@Slf4j
class ProcessIdLoggingServerLifecycleListener implements ServerLifecycleListener {
    private final Long processId;

    ProcessIdLoggingServerLifecycleListener(Long processId) {
        this.processId = processId;
    }

    @Override
    public void serverStarted(Server server) {
        if (nonNull(processId)) {
            LOG.info("Application running as process {}", processId);
        } else {
            LOG.warn("Unable to obtain the process ID");
        }
    }
}
