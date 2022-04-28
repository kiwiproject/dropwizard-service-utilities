package org.kiwiproject.dropwizard.util.lifecycle;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProcessIdLoggingServerLifecycleListener")
class ProcessIdLoggingServerLifecycleListenerTest {

    @Test
    void shouldNotThrowException_WhenPidProvided_AsPrimitiveLong() {
        var listener = new ProcessIdLoggingServerLifecycleListener(10_000L);
        var server = new Server(8080);

        assertThatCode(() -> listener.serverStarted(server)).doesNotThrowAnyException();
    }

    @Test
    void shouldNotThrowException_WhenPidProvided_AsWrapperLong() {
        var listener = new ProcessIdLoggingServerLifecycleListener(Long.valueOf(20_000L));
        var server = new Server(8080);

        assertThatCode(() -> listener.serverStarted(server)).doesNotThrowAnyException();
    }

    @Test
    void shouldNotThrowException_WhenPidNotProvided() {
        var listener = new ProcessIdLoggingServerLifecycleListener(null);
        var server = new Server(8080);

        assertThatCode(() -> listener.serverStarted(server)).doesNotThrowAnyException();
    }
}
