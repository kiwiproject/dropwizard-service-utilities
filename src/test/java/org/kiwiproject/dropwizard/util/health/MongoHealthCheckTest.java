package org.kiwiproject.dropwizard.util.health;

import static org.kiwiproject.test.assertj.dropwizard.metrics.HealthCheckResultAssertions.assertThatHealthCheck;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.Mongo;
import com.mongodb.ServerAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.metrics.health.HealthStatus;

@SuppressWarnings("deprecation")
@DisplayName("MongoHealthCheck")
class MongoHealthCheckTest {

    private static final String DB_NAME = "someName";
    private static final String SERVER_ADDRESS = "localhost:27017";

    @Nested
    class IsHealthy {

        @Test
        void whenGetStatsReturnsSuccessfully() {
            var mockDb = createMockedMongoDb(true);
            var healthCheck = new MongoHealthCheck(mockDb);

            assertThatHealthCheck(healthCheck)
                    .isHealthy()
                    .hasMessage("Mongo " + SERVER_ADDRESS + "/" + DB_NAME + " is up")
                    .hasDetailsContainingKeys("storageSize", "dataSize");
        }
    }

    @SuppressWarnings("deprecation")
    @Nested
    class IsUnhealthy {

        @Test
        void whenExceptionIsThrown() {
            var mockDb = setupMockDB();
            when(mockDb.getStats()).thenThrow(new RuntimeException("oops"));

            var healthCheck = new MongoHealthCheck(mockDb);

            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasDetail("severity", HealthStatus.CRITICAL.name())
                    .hasMessageStartingWith("Mongo " + SERVER_ADDRESS + "/" + DB_NAME + " is not up: oops");

        }

        @Test
        void whenGetStatsFails() {
            var mockDb = createMockedMongoDb(false);
            var healthCheck = new MongoHealthCheck(mockDb);

            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasMessageStartingWith("Failed to retrieve db stats " + SERVER_ADDRESS + "/" + DB_NAME + " : ");
        }
    }

    private static DB createMockedMongoDb(boolean getStatsSuccessful) {
        var mockDb = setupMockDB();
        var mockedResult = mockedCommandResult(getStatsSuccessful);

        when(mockDb.getStats()).thenReturn(mockedResult);
        return mockDb;
    }

    private static DB setupMockDB() {
        var mockDb = mock(DB.class);
        var mockMongo = mock(Mongo.class);
        var mockServerAddress = mock(ServerAddress.class);

        when(mockDb.getMongo()).thenReturn(mockMongo);
        when(mockMongo.getAddress()).thenReturn(mockServerAddress);
        when(mockServerAddress.toString()).thenReturn(MongoHealthCheckTest.SERVER_ADDRESS);
        when(mockDb.getName()).thenReturn(MongoHealthCheckTest.DB_NAME);

        return mockDb;
    }

    private static CommandResult mockedCommandResult(boolean ok) {
        var result = mock(CommandResult.class);
        when(result.ok()).thenReturn(ok);
        return result;
    }
}
