package org.kiwiproject.dropwizard.util.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @Nested
    class Get {

        @Test
        void shouldReturnExpectedFormat_UsingActualProcess() {
            var load = new ServerLoadFetcher().get().orElse(null);
            assertThat(load)
                    .isNotNull()
                    .matches("^\\d+.\\d+,? \\d+.\\d+,? \\d+.\\d+$");
        }

        @ParameterizedTest
        @CsvSource({
                "'18:29:28 up 34 days, 18:25, 1 user, load average: 0.88, 0.98, 1.03', '0.88, 0.98, 1.03'",
                "'18:29:28 up 34 days, 18:25, 1 user, Load Average: 0.88, 0.98, 1.03', '0.88, 0.98, 1.03'",
                "'18:29:28 up 34 days, 18:25, 1 user, load averages: 0.88, 0.98, 1.03', '0.88, 0.98, 1.03'",
                "'18:29:28 up 34 days, 18:25, 1 user, Load Averages: 0.88, 0.98, 1.03', '0.88, 0.98, 1.03'",
                "'18:29:28 up 34 days, 18:25, 1 user, load averages: 0.88 0.98 1.03', '0.88 0.98 1.03'",
                "'18:29:28 up 34 days, 18:25, 1 user, Load Averages: 0.88 0.98 1.03', '0.88 0.98 1.03'",
                "'18:29:28 up 34 days, 18:25, 1 user, load averages: 0.88 0.98 1.03', '0.88 0.98 1.03'",
                "'18:29:28 up 34 days, 18:25, 1 user, Load Averages: 0.88 0.98 1.03', '0.88 0.98 1.03'"
        })
        void shouldReturnLoadAverages_UsingMockProcess(String output, String expectedLoadAverages) throws InterruptedException {
            var proc = mock(Process.class);
            when(processes.launch("uptime")).thenReturn(proc);

            var bais = new ByteArrayInputStream(output.getBytes());

            when(proc.getInputStream()).thenReturn(bais);
            when(proc.waitFor(anyLong(), any())).thenReturn(true);

            var load = serverLoad.get().orElse(null);
            assertThat(load)
                    .isNotNull()
                    .isEqualTo(expectedLoadAverages);
        }

        @Test
        void shouldReturnEmptyOptional_WhenLoadAverageString_IsNotPresent() {
            var output = "this is total garbage in, so garbage out";
            var proc = mock(Process.class);
            when(processes.launch("uptime")).thenReturn(proc);

            var bais = new ByteArrayInputStream(output.getBytes());
            when(proc.getInputStream()).thenReturn(bais);

            var load = serverLoad.get().orElse(null);
            assertThat(load).isNull();
        }

        @Test
        void shouldReturnEmptyOptional_WhenProcessWaitTimesOut() throws InterruptedException {
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
}
