package org.kiwiproject.dropwizard.util.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.base.KiwiStrings.f;
import static org.kiwiproject.test.assertj.dropwizard.metrics.HealthCheckResultAssertions.assertThatHealthCheck;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.mongodb.client.MongoDatabase;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kiwiproject.collect.KiwiMaps;
import org.kiwiproject.metrics.health.HealthStatus;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.OngoingStubbing;

@DisplayName("MongoHealthCheck")
class MongoHealthCheckTest {

    private static final String DB_NAME = "testDatabase";
    private static final String OK_PROPERTY = "ok";
    private static final String ERROR_MESSAGE_PROPERTY = "errmsg";
    private static final String CODE_PROPERTY = "code";
    private static final String CODE_NAME_PROPERTY = "codeName";

    @Nested
    class IsHealthy {

        @ParameterizedTest
        @MethodSource("org.kiwiproject.dropwizard.util.health.MongoHealthCheckTest#successfulCommandResults")
        void whenPingSucceeds(Document commandResult) {
            var database = setupMockMongoDatabase();
            whenReceivesRunCommand(database).thenReturn(commandResult);

            var healthCheck = new MongoHealthCheck(database);

            assertThatHealthCheck(healthCheck)
                    .isHealthy()
                    .hasMessage(f("Successfully pinged Mongo database {}", DB_NAME))
                    .hasDetail("severity", HealthStatus.OK.name())
                    .hasDetail(OK_PROPERTY, commandResult.get(OK_PROPERTY))
                    .doesNotHaveDetailsContainingKeys(ERROR_MESSAGE_PROPERTY, CODE_PROPERTY, CODE_NAME_PROPERTY);

            verifyRunPingCommand(database);
        }
    }

    @Nested
    class IsUnhealthy {

        @Test
        void whenExceptionIsThrown() {
            var database = setupMockMongoDatabase();
            var message = "oops";
            whenReceivesRunCommand(database).thenThrow(new RuntimeException(message));

            var healthCheck = new MongoHealthCheck(database);

            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasMessage(f("Error pinging Mongo database {} ({}: {})",
                            DB_NAME, RuntimeException.class.getName(), message))
                    .hasDetail("severity", HealthStatus.CRITICAL.name())
                    .doesNotHaveDetailsContainingKeys(OK_PROPERTY, ERROR_MESSAGE_PROPERTY, CODE_PROPERTY, CODE_NAME_PROPERTY);

            verifyRunPingCommand(database);
        }

        @ParameterizedTest
        @MethodSource("org.kiwiproject.dropwizard.util.health.MongoHealthCheckTest#failedCommandResults")
        void whenCommandFails(Document commandResult) {
            var database = setupMockMongoDatabase();
            whenReceivesRunCommand(database).thenReturn(commandResult);

            var healthCheck = new MongoHealthCheck(database);

            var expectedErrmsgValue = Optional.ofNullable(commandResult.get(ERROR_MESSAGE_PROPERTY))
                    .orElse("[No errmsg value]");

            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasMessage(f("Error pinging Mongo database {} : {}", DB_NAME, expectedErrmsgValue))
                    .hasDetail("severity", HealthStatus.WARN.name())
                    .hasDetail(OK_PROPERTY, commandResult.get(OK_PROPERTY))
                    .hasDetail(CODE_PROPERTY, commandResult.get(CODE_PROPERTY))
                    .hasDetail(CODE_NAME_PROPERTY, commandResult.get(CODE_NAME_PROPERTY));

            verifyRunPingCommand(database);
        }
    }

    private static MongoDatabase setupMockMongoDatabase() {
        var mockDatabase = mock(MongoDatabase.class);
        when(mockDatabase.getName()).thenReturn(DB_NAME);
        return mockDatabase;
    }

    private OngoingStubbing<Document> whenReceivesRunCommand(MongoDatabase database) {
        return when(database.runCommand(any(BsonDocument.class)));
    }

    private static List<Document> successfulCommandResults() {
        var ok = OK_PROPERTY;
        return List.of(
            new Document(ok, true),
            new Document(ok, 1),
            new Document(ok, 1L),
            new Document(ok, 1.0F),
            new Document(ok, 1.0)
        );
    }

    private static List<Document> failedCommandResults() {
        return List.of(
            newFailedDocument("no such command 'crash'", 59, "CommandNotFound"),
            newFailedDocument("scale has to be a number > 0", null, null),
            newFailedDocument("an error occurred", 42, "The Code", false),
            newFailedDocument("unknown error", null, null, null),
            newFailedDocument(null, null, null, "true")
        );
    }

    private static Document newFailedDocument(String errmsg, Integer code, String codeName) {
        return newFailedDocument(errmsg, code, codeName, 0.0);
    }

    private static Document newFailedDocument(String errmsg, Integer code, String codeName, Object okValue) {
        return new Document(KiwiMaps.newHashMap(
            OK_PROPERTY, okValue,
            ERROR_MESSAGE_PROPERTY, errmsg,
            CODE_PROPERTY, code,
            CODE_NAME_PROPERTY, codeName
        ));
    }

    private void verifyRunPingCommand(MongoDatabase database) {
        var inputCaptor = ArgumentCaptor.forClass(BsonDocument.class);
        verify(database).runCommand(inputCaptor.capture());
        var bsonInputDoc = inputCaptor.getValue();
        assertThat(bsonInputDoc).containsEntry("ping", new BsonInt32(1));
    }
}
