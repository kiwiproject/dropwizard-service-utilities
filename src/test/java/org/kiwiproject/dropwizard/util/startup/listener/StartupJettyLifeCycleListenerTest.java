package org.kiwiproject.dropwizard.util.startup.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.dropwizard.util.startup.SystemExecutioner;

@DisplayName("StartupJettyLifeCycleListener")
class StartupJettyLifeCycleListenerTest {

    @Test
    void testLifeCycleFailure() {
        var executioner = mock(SystemExecutioner.class);
        var listener = new StartupJettyLifeCycleListener(executioner);

        listener.lifeCycleFailure(null, new Exception("ARGHHH!"));
        verify(executioner).exit();
    }
}
