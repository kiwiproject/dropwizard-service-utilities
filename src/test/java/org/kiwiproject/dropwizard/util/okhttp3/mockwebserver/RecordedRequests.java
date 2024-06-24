package org.kiwiproject.dropwizard.util.okhttp3.mockwebserver;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Some test utilities for getting {@link RecordedRequest} instances from a {@link MockWebServer}.
 */
@UtilityClass
@Slf4j
public class RecordedRequests {

    public static RecordedRequest takeRequiredRequest(MockWebServer mockWebServer) {
        return takeRequestOrEmpty(mockWebServer)
                .orElseThrow(() -> new IllegalStateException("no request available"));
    }

    public static Optional<RecordedRequest> takeRequestOrEmpty(MockWebServer mockWebServer) {
        return Optional.ofNullable(takeRequestOrNull(mockWebServer));
    }

    public static void assertNoMoreRequests(MockWebServer mockWebServer) {
        assertThat(takeRequestOrNull(mockWebServer))
                .describedAs("There should not be any more requests, but (at least) one was found")
                .isNull();
    }

    public static RecordedRequest takeRequestOrNull(MockWebServer mockWebServer) {
        try {
            return mockWebServer.takeRequest(10, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOG.info("Interrupted waiting to get next request");
            return null;
        }
    }
}
