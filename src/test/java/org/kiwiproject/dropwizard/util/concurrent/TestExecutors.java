package org.kiwiproject.dropwizard.util.concurrent;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class TestExecutors {

    /**
     * Perform actions using the given executor inside the Consumer, then shut the executor service down and
     * await termination (with timeout).
     */
    public static void use(ScheduledExecutorService executor, Consumer<ScheduledExecutorService> consumer)
            throws InterruptedException {

        try {
            consumer.accept(executor);
        } finally {
            executor.shutdown();
            var terminatedOk = executor.awaitTermination(500, TimeUnit.MILLISECONDS);
            LOG.info("terminated OK within timeout period? {}", terminatedOk);
        }
    }
}
