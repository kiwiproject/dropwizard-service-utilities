package org.kiwiproject.dropwizard.util.task;

import io.dropwizard.servlets.tasks.Task;
import org.kiwiproject.dropwizard.util.metrics.ServerLoadFetcher;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * Send a POST on the admin port to {@code /tasks/serverLoad} to get the current server load average.
 * <p>
 * If the load average was not obtainable for some reason, returns {@link ServerLoadFetcher#NO_VALUE}.
 */
public class ServerLoadTask extends Task {

    private static final String TASK_NAME = "serverLoad";

    private final ServerLoadFetcher serverLoad;

    public ServerLoadTask() {
        super(TASK_NAME);
        serverLoad = new ServerLoadFetcher();
    }

    @Override
    public void execute(Map<String, List<String>> parameters, PrintWriter output) {
        var value = serverLoad.get().orElse(ServerLoadFetcher.NO_VALUE);
        output.println(value);
        output.flush();
    }
}
