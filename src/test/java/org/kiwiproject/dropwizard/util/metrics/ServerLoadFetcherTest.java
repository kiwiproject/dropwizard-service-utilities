package org.kiwiproject.dropwizard.util.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.base.process.ProcessHelper;

import java.io.ByteArrayInputStream;

@DisplayName("ServerLoadFetcher")
class ServerLoadFetcherTest {

    private ServerLoadFetcher serverLoad;
    private ProcessHelper processes;

    @BeforeEach
    void setUp() {
        processes = mock(ProcessHelper.class);
        serverLoad = new ServerLoadFetcher(processes);
    }

    @Test
    void testGet_UsingActualProcess() {
        var load = new ServerLoadFetcher().get().orElse(null);
        assertThat(load)
                .isNotNull()
                .matches("^\\d+.\\d+[,]? \\d+.\\d+[,]? \\d+.\\d+$");
    }

    @Test
    void testGet_UsingMockProcess() throws InterruptedException {
        var output = "18:29:28 up 34 days, 18:25, 1 user, load average: 0.88, 0.98, 1.03";
        var proc = mock(Process.class);
        when(processes.launch("uptime")).thenReturn(proc);

        var bais = new ByteArrayInputStream(output.getBytes());

        when(proc.getInputStream()).thenReturn(bais);
        when(proc.waitFor(anyLong(), any())).thenReturn(true);

        var load = serverLoad.get().orElse(null);
        assertThat(load)
                .isNotNull()
                .isEqualTo("0.88, 0.98, 1.03");
    }

    @Test
    void testGet_WhenLoadAverageString_IsNotPresent() {
        var output = "this is total garbage in, so garbage out";
        var proc = mock(Process.class);
        when(processes.launch("uptime")).thenReturn(proc);

        var bais = new ByteArrayInputStream(output.getBytes());
        when(proc.getInputStream()).thenReturn(bais);

        var load = serverLoad.get().orElse(null);
        assertThat(load).isNull();
    }

    @Test
    void testGet_WhenProcessWaitTimesOut() throws InterruptedException {
        var output = "18:29:28 up 34 days, 18:25, 1 user, load average: 0.88, 0.98, 1.03";
        var proc = mock(Process.class);
        when(processes.launch("uptime")).thenReturn(proc);

        var bais = new ByteArrayInputStream(output.getBytes());
        when(proc.getInputStream()).thenReturn(bais);
        when(proc.waitFor(anyLong(), any())).thenReturn(false);

        var load = serverLoad.get().orElse(null);
        assertThat(load).isNull();
    }
}
