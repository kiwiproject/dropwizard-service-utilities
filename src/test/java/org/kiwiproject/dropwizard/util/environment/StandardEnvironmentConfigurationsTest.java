package org.kiwiproject.dropwizard.util.environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jersey.DropwizardResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.test.dropwizard.mockito.DropwizardMockitoMocks;
import org.kiwiproject.validation.KiwiValidations;

import javax.validation.Validator;

@DisplayName("StandardEnvironmentConfigurations")
class StandardEnvironmentConfigurationsTest {

    @Nested
    class RegisterWadlGeneration {

        @Test
        void shouldEnableWadlGeneration() {
            var resourceConfig = new DropwizardResourceConfig();
            var environment = DropwizardMockitoMocks.mockEnvironment();

            when(environment.jersey().getResourceConfig()).thenReturn(resourceConfig);

            StandardEnvironmentConfigurations.enableWadlGeneration(environment);

            assertThat(resourceConfig.getProperties()).contains(entry(ServerProperties.WADL_FEATURE_DISABLE, false));
        }
    }

    @Nested
    class ConfigureKiwiValidator {

        @Test
        void shouldSetTheKiwiValidator() {
            var validator = mock(Validator.class);
            var environment = DropwizardMockitoMocks.mockEnvironment(new ObjectMapper(), validator);

            StandardEnvironmentConfigurations.configureKiwiValidator(environment);

            assertThat(KiwiValidations.getValidator()).isSameAs(validator);
        }
    }

}
