package org.kiwiproject.dropwizard.util.metrics;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.base.process.ProcessHelper;
import org.kiwiproject.io.KiwiIO;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Attempts to get the server load average as reported by the {@code uptime} command.
 *
 * @implNote Only works on *nix-like systems, or maybe in Windows if there is an uptime command installed and on the PATH.
 */
@Slf4j
public class ServerLoadFetcher {

    /**
     * Can be used if you need to report some value for load average in the case when {@link #get()} returns an
     * empty {@link Optional}
     */
    public static final String NO_VALUE = "0.00, 0.00, 0.00";

    private static final Pattern LOAD_AVERAGE_PATTERN = Pattern.compile("load average[s]?: (.*)");

    private final ProcessHelper processes;

    public ServerLoadFetcher() {
        this(new ProcessHelper());
    }

    @VisibleForTesting
    ServerLoadFetcher(ProcessHelper processes) {
        this.processes = processes;
    }

    /**
     * Example format on Linux-based systems:
     * <p>
     * {@code 18:29:28 up 34 days, 18:25, 1 user, load average: 0.88, 0.98, 1.03}
     * <p>
     * Example format on macOS systems:
     * <p>
     * {@code 18:29:28 up 34 days, 18:25, 1 user, load averages: 0.88 0.98 1.03}
     *
     * @return an {@link Optional} with the load average string or {@link Optional#empty()} if not found
     */
    public Optional<String> get() {
        var process = processes.launch("uptime");

        try {
            var output = KiwiIO.readInputStreamOf(process);
            var matcher = LOAD_AVERAGE_PATTERN.matcher(output);
            checkState(matcher.find(), "Did not find load average substring");

            var exitedBeforeTimeout = process.waitFor(500, TimeUnit.MILLISECONDS);
            if (!exitedBeforeTimeout) {
                LOG.debug("Process did not exit before timeout, so assume something is not right");
                return Optional.empty();
            }

            return Optional.of(matcher.group(1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while getting server load average", e);
            return Optional.empty();
        } catch (Exception e) {
            LOG.debug("Error getting server load average", e);
            return Optional.empty();
        }
    }
}
