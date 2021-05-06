package org.kiwiproject.dropwizard.util.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.dropwizard.util.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("JobSchedule")
class JobScheduleTest {

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

            assertThat(jobSchedule.getInitialDelay()).isEqualTo(JobSchedule.DEFAULT_INITIAL_DELAY);
            assertThat(jobSchedule.getIntervalDelay().toSeconds()).isEqualTo(SECONDS);
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

            assertThat(jobSchedule.getInitialDelay()).isEqualTo(JobSchedule.DEFAULT_INITIAL_DELAY);
            assertThat(jobSchedule.getIntervalDelay().toSeconds()).isEqualTo(SECONDS);
        }
    }
}
