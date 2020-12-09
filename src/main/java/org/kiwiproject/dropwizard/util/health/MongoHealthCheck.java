package org.kiwiproject.dropwizard.util.health;

import static org.kiwiproject.metrics.health.HealthCheckResults.newResultBuilder;
import static org.kiwiproject.metrics.health.HealthCheckResults.newUnhealthyResult;

import com.codahale.metrics.health.HealthCheck;
import com.mongodb.DB;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.metrics.health.HealthStatus;

/**
 * Health check that attempts to pull dbStats from Mongo and report on the status.
 * <p>
 * Note this health check is currently using the deprecated {@link DB} class.
 */
@Slf4j
public class MongoHealthCheck extends HealthCheck {

    public static final String TITLE = "Mongo";

    private final DB db;

    public MongoHealthCheck(DB db) {
        this.db = db;
    }


    @SuppressWarnings({"deprecation", "ConstantConditions"})
    @Override
    protected Result check() throws Exception {
        try {
            var host = db.getMongo().getAddress().toString();
            var dbName = db.getName();

            var result = db.getStats();

            var isHealthy = result.ok();
            var resultBuilder = newResultBuilder(isHealthy)
                    .withDetail("storageSize", result.get("storageSize"))
                    .withDetail("dataSize", result.get("dataSize"));

            if (isHealthy) {
                return resultBuilder.withMessage("Mongo %s/%s is up", host, dbName).build();
            }

            return resultBuilder
                    .withMessage("Failed to retrieve db stats %s/%s : %s",
                            host, dbName, result.getErrorMessage())
                    .build();
        } catch (Exception e) {
            LOG.error("Unable to connect to Mongo: {}", e.getMessage(), e);
            return newUnhealthyResult(HealthStatus.CRITICAL, "Mongo is not up: " + e.getMessage());
        }
    }
}
