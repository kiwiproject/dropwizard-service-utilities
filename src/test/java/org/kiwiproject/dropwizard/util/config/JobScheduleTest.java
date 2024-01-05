package org.kiwiproject.dropwizard.util.config;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.kiwiproject.test.constants.KiwiTestConstants.JSON_HELPER;

import io.dropwizard.util.Duration;
import lombok.Getter;
import lombok.Setter;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.yaml.YamlHelper;

@DisplayName("JobSchedule")
class JobScheduleTest {

    @Nested
    class Construction {

        @Test
        void shouldConstructUsingNoArgsConstructor() {
            var jobSchedule = new JobSchedule();

            assertDelays(jobSchedule, JobSchedule.DEFAULT_INITIAL_DELAY, null);
        }

        @Test
        void shouldConstructUsingBuilderDefaults() {
            var jobSchedule = JobSchedule.builder().build();

            assertDelays(jobSchedule, JobSchedule.DEFAULT_INITIAL_DELAY, null);
        }

        @Test
        void shouldConstructUsingBuilder() {
            var jobSchedule = JobSchedule.builder()
                    .initialDelay(Duration.seconds(5))
                    .intervalDelay(Duration.minutes(10))
                    .build();

            assertDelays(jobSchedule, Duration.seconds(5), Duration.minutes(10));
        }
    }

    @Nested
    class Deserialization {

        @Test
        void shouldDeserializeFromJson_WithBothProperties() {
            var json = """
                    {
                        "schedule": {
                            "initialDelay": "10 seconds",
                            "intervalDelay": "15 minutes"
                        }
                    }
                    """;

            var config = JSON_HELPER.toObject(json, Config.class);
            var jobSchedule = config.getSchedule();

            assertDelays(jobSchedule, Duration.seconds(10), Duration.minutes(15));
        }

        @Test
        void shouldDeserializeFromJson_AndRespectDefaultInitialDelay() {
            var json = """
                    {
                        "schedule": {
                            "intervalDelay": "2 minutes"
                        }
                    }
                    """;

            var config = JSON_HELPER.toObject(json, Config.class);
            var jobSchedule = config.getSchedule();

            assertDelays(jobSchedule, JobSchedule.DEFAULT_INITIAL_DELAY, Duration.minutes(2));
        }

        @Test
        void shouldDeserializeFromYaml_WithBothProperties() {
            var yaml = """
                    ---
                    schedule:
                        initialDelay: 20 seconds
                        intervalDelay: 5 minutes
                    """;

            var config = new YamlHelper().toObject(yaml, Config.class);
            var jobSchedule = config.getSchedule();

            assertDelays(jobSchedule, Duration.seconds(20), Duration.minutes(5));
        }

        @Test
        void shouldDeserializeFromYaml_AndRespectDefaultInitialDelay() {
            var yaml = """
                    ---
                    schedule:
                        intervalDelay: 180 seconds
                    """;

            var config = new YamlHelper().toObject(yaml, Config.class);
            var jobSchedule = config.getSchedule();

            assertDelays(jobSchedule, JobSchedule.DEFAULT_INITIAL_DELAY, Duration.seconds(180));
        }

        @Getter
        @Setter
        public static class Config {
            private JobSchedule schedule;
        }
    }

    @Nested
    class OfIntervalDelay {

        public static final int SECONDS = 45;

        @Test
        void shouldNotPermitNullDropwizardDuration() {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    JobSchedule.ofIntervalDelay((Duration) null))
                    .withMessage("intervalDelay must not be null");
        }

        @Test
        void shouldCreateInstanceWithDropwizardDuration() {
            var jobSchedule = JobSchedule.ofIntervalDelay(Duration.seconds(SECONDS));

            assertDelays(jobSchedule, JobSchedule.DEFAULT_INITIAL_DELAY, Duration.seconds(SECONDS));
        }

        @Test
        void shouldNotPermitNullJavaTimeDuration() {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    JobSchedule.ofIntervalDelay((java.time.Duration) null))
                    .withMessage("intervalDelay must not be null");
        }

        @Test
        void shouldCreateInstanceWithJavaTimeDuration() {
            var jobSchedule = JobSchedule.ofIntervalDelay(java.time.Duration.ofSeconds(SECONDS));

            assertDelays(jobSchedule, JobSchedule.DEFAULT_INITIAL_DELAY, Duration.seconds(SECONDS));
        }
    }

    private static void assertDelays(JobSchedule jobSchedule,
                                     Duration expectedInitialDelay,
                                     @Nullable Duration expectedIntervalDelay) {

        // Dropwizard Duration#equals compares value and units while its #compareTo
        // compares the actual duration, such that:
        //
        // Duration.seconds(1).equals(Duration.milliseconds(1_000)) is false
        // Duration.seconds(1).compareTo(Duration.milliseconds(1_000)) is zero (equal)
        //
        // So we compare here using compareTo.

        assertAll(
                () -> assertThat(jobSchedule.getInitialDelay())
                        .describedAs("initialDelay should never be null and should equal expected value")
                        .isNotNull()
                        .isEqualByComparingTo(expectedInitialDelay),

                () -> {
                    if (isNull(expectedIntervalDelay)) {
                        assertThat(jobSchedule.getIntervalDelay())
                                .describedAs("intervalDelay expected to be null")
                                .isNull();
                    } else {
                        assertThat(jobSchedule.getIntervalDelay())
                                .describedAs("intervalDelay should be non-null and equal to expected value")
                                .isEqualByComparingTo(expectedIntervalDelay);
                    }
                }
        );
    }
}
