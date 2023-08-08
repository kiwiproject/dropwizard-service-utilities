package org.kiwiproject.dropwizard.util.health;

import static org.kiwiproject.base.KiwiStrings.f;
import static org.kiwiproject.metrics.health.HealthCheckResults.newResultBuilder;
import static org.kiwiproject.metrics.health.HealthCheckResults.newUnhealthyResult;

import com.codahale.metrics.health.HealthCheck;
import com.mongodb.client.MongoDatabase;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.Document;

import java.util.Optional;

/**
 * Health check that attempts to issue a 'ping' command to a Mongo database.
 *
 * @see <a href="https://www.mongodb.com/docs/manual/reference/command/ping/">mongo ping command</a>
 */
@Slf4j
public class MongoHealthCheck extends HealthCheck {

    private static final String NO_ERRMSG_VALUE = "[No errmsg value]";

    /**
     * A default name that can be used when registering this health check.
     */
    @SuppressWarnings("unused")
    public static final String DEFAULT_NAME = "Mongo";

    private final MongoDatabase database;
    private final String dbName;

    public MongoHealthCheck(MongoDatabase database) {
        this.database = database;
        this.dbName = database.getName();
    }

    /**
     * Checks the health of a Mongo database by issuing a Mongo ping command.
     * <p>
     * Results always include the value of the "ok" property in the details. When the ping
     * command fails (without throwing an exception), the details will also include the
     * following from the result: "errmsg", "code", "codeName".
     * <p>
     * For example, if you send an unknown command to Mongo, the "code" will be 59 and
     * the "codeName" will be the string "CommandNotFound". Note also that "code" and
     * "codeName" can be null. For a list of MongoDB error codes, see
     * <a href="https://github.com/mongodb/mongo/blob/master/src/mongo/base/error_codes.yml">here</a>.
     */
    @Override
    protected Result check() {
        try {
            var pingCommand = new BsonDocument("ping", new BsonInt32(1));
            var commandResult = database.runCommand(pingCommand);
            LOG.trace("ping result: {}", commandResult);

            var okValue = commandResult.get("ok");
            var isHealthy = ok(okValue);
            var resultBuilder = newResultBuilder(isHealthy).withDetail("ok", okValue);

            if (isHealthy) {
                return resultBuilder
                    .withMessage("Successfully pinged Mongo database %s", dbName)
                    .build();
            }

            var errorMessage = errorMessage(commandResult).orElse(NO_ERRMSG_VALUE);
            return resultBuilder
                .withMessage("Error pinging Mongo database %s : %s", dbName, errorMessage)
                .withDetail("code", commandResult.get("code"))
                .withDetail("codeName", commandResult.get("codeName"))
                .build();

        } catch (Exception e) {
            var message = f("Error pinging Mongo database {} ({}: {})",
                    dbName, e.getClass().getName(), e.getMessage());
            LOG.error(message, e);
            return newUnhealthyResult(e, message);
        }
    }

    /**
     * @implNote The old Mongo 3.x driver had a CommandResult class that encapsulated the
     * logic to check if commands were successful. It checked whether the 'ok' property in the
     * result Document was a boolean, which is why this method is checking for both numeric
     * and boolean values. The Mongo docs say that commands always return a 0 or 1 (integer)
     * as the value of the 'ok' property, but in reality it returns 0.0 (double), so I decided
     * to leave the original logic to be conservative. However, since the result should be a
     * number, this performs the check for a number first, whereas the CommandResult checked
     * for a boolean first.
     */
    private static boolean ok(Object okValue) {
        if (okValue instanceof Number number) {
            return number.intValue() == 1;
        } else if (okValue instanceof Boolean) {
            return (boolean) okValue;
        } else {
            LOG.warn("Received unexpected {} value in ping result for 'ok': {}",
                    classNameOrNull(okValue),okValue);
            return false;
        }
    }

    private static String classNameOrNull(Object value) {
        return Optional.ofNullable(value)
                .map(Object::getClass)
                .map(Class::getName)
                .orElse(null);
    }

    private static Optional<String> errorMessage(Document commandResult) {
        var errmsgValue = commandResult.get("errmsg");
        return Optional.ofNullable(errmsgValue).map(Object::toString);
    }
}
