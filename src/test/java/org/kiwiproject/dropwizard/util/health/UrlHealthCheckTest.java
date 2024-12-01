package org.kiwiproject.dropwizard.util.health;

import static org.kiwiproject.test.assertj.dropwizard.metrics.HealthCheckResultAssertions.assertThatHealthCheck;
import static org.kiwiproject.test.okhttp3.mockwebserver.RecordedRequestAssertions.assertThatRecordedRequest;
import static org.kiwiproject.test.okhttp3.mockwebserver.RecordedRequests.assertNoMoreRequests;
import static org.kiwiproject.test.okhttp3.mockwebserver.RecordedRequests.takeRequiredRequest;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.io.KiwiIO;
import org.kiwiproject.jersey.client.ClientBuilders;
import org.kiwiproject.metrics.health.HealthCheckResults;
import org.kiwiproject.metrics.health.HealthStatus;
import org.kiwiproject.registry.client.NoOpRegistryClient;

import java.time.Instant;

@DisplayName("UrlHealthCheck")
class UrlHealthCheckTest {

    private static final String STATUS_PATH = "/status";

    private MockWebServer server;
    private Client client;
    private String description;
    private String url;
    private KiwiEnvironment kiwiEnvironment;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        client = ClientBuilders.jersey()
                .connectTimeout(500)
                .readTimeout(500)
                .registryClient(new NoOpRegistryClient())
                .hostnameVerifier(new NoopHostnameVerifier())
                .build();

        description = RandomStringUtils.secure().nextAlphabetic(15);
        url = server.url(STATUS_PATH).toString();
        kiwiEnvironment = mock(KiwiEnvironment.class);
    }

    @AfterEach
    void tearDown() {
        KiwiIO.closeQuietly(server);
        client.close();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = { false })
    void shouldNotPerformCheck_WhenExecutionCondition_EvaluatesToFalse(Boolean result) {
        var healthCheck = new UrlHealthCheck(client, description, url, () -> result);

        assertThatHealthCheck(healthCheck)
                .isHealthy()
                .hasMessage("executionCondition evaluated to false; check skipped and reported as healthy")
                .hasDetail(HealthCheckResults.SEVERITY_DETAIL, HealthStatus.OK.name());
    }

    @Test
    void shouldExecuteHealthCheck_IfExecutionCondition_ThrowsException() {
        var healthCheck = new UrlHealthCheck(client, description, url, () -> {
            throw new RuntimeException("Error calling executionCondition");
        });

        server.enqueue(new MockResponse().setResponseCode(200));

        assertThatHealthCheck(healthCheck)
                .isHealthy()
                .hasMessageStartingWith("Got successful 200 response")
                .hasDetail(HealthCheckResults.SEVERITY_DETAIL, HealthStatus.OK.name());

        verifyRequest();
    }

    @Test
    void shouldCreateUsingMinimalConstructor() {
        var healthCheck = new UrlHealthCheck(client, description, url);

        server.enqueue(new MockResponse().setResponseCode(200));

        assertThatHealthCheck(healthCheck)
                .hasMessageStartingWith("Got successful 200 response")
                .isHealthy();

        verifyRequest();
    }

    @ParameterizedTest
    @ValueSource(ints = { 200, 201, 202, 204 })
    void shouldReturnHealthy_WhenReceivesSuccessfulResponse(int statusCode) {
        var healthCheck = new UrlHealthCheck(client, description, url, () -> true, kiwiEnvironment);

        var now = Instant.now();
        when(kiwiEnvironment.currentInstant()).thenReturn(now);

        server.enqueue(new MockResponse().setResponseCode(statusCode));

        assertThatHealthCheck(healthCheck)
                .isHealthy()
                .hasMessage("Got successful {} response from {} at {} (checked at: {})",
                        statusCode, description, url, UrlHealthCheck.readableInstant(now))
                .hasDetail(HealthCheckResults.SEVERITY_DETAIL, HealthStatus.OK.name());

        verify(kiwiEnvironment, only()).currentInstant();

        verifyRequest();
    }

    @Test
    void shouldReturnUnhealthyResult_WithCriticalSeverity_WhenServerIsNotAvailable() {
        var healthCheck = new UrlHealthCheck(client, description, url, () -> true, kiwiEnvironment);

        var now = Instant.now();
        when(kiwiEnvironment.currentInstant()).thenReturn(now);

        // don't enqueue any responses...

        assertThatHealthCheck(healthCheck)
                .isUnhealthy()
                .hasMessage("Got {} making call to {} at {}." +
                        " It may be down or unreachable! (checked at: {})",
                        ProcessingException.class.getName(), description, url, UrlHealthCheck.readableInstant(now))
                .hasDetail(HealthCheckResults.SEVERITY_DETAIL, HealthStatus.CRITICAL.name());
    }

    @ParameterizedTest
    @ValueSource(ints = { 400, 404, 500, 501, 503 })
    void shouldReturnUnhealthyResult_WithWarnLevelSeverity_WhenReceivesUnsuccessfulResponse(int statusCode) {
        var healthCheck = new UrlHealthCheck(client, description, url, () -> true, kiwiEnvironment);

        var now = Instant.now();
        when(kiwiEnvironment.currentInstant()).thenReturn(now);

        server.enqueue(new MockResponse().setResponseCode(statusCode));

        assertThatHealthCheck(healthCheck)
                .isUnhealthy()
                .hasMessage("Got unsuccessful {} response from {} at {}." +
                        " It may not be functioning properly. (checked at: {})",
                        statusCode, description, url, UrlHealthCheck.readableInstant(now))
                .hasDetail(HealthCheckResults.SEVERITY_DETAIL, HealthStatus.WARN.name());

        verify(kiwiEnvironment, only()).currentInstant();

        verifyRequest();
    }

    private void verifyRequest() {
        var request = takeRequiredRequest(server);
        assertThatRecordedRequest(request)
                .isGET()
                .hasPath(STATUS_PATH);

        assertNoMoreRequests(server);
    }
}
