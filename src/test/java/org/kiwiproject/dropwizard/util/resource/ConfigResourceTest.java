package org.kiwiproject.dropwizard.util.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kiwiproject.jaxrs.KiwiGenericTypes.MAP_OF_STRING_TO_OBJECT_GENERIC_TYPE;
import static org.kiwiproject.test.constants.KiwiTestConstants.JSON_HELPER;

import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.Issue;
import org.kiwiproject.json.RuntimeJsonException;
import org.kiwiproject.test.assertj.KiwiAssertJ;
import org.kiwiproject.test.junit.jupiter.ClearBoxTest;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@DisplayName("ConfigResource")
@ExtendWith(DropwizardExtensionsSupport.class)
class ConfigResourceTest {

    @Getter
    @Setter
    @AllArgsConstructor
    static class TestConfig extends Configuration {
        private String someNeededProperty;
        private String someHiddenPassword;
        private CacheConfig cacheConfig;
    }

    /**
     * Class that simulates the problematic configuration class that was the cause of issues #289 and #290.
     *
     * @link <a href="https://github.com/kiwiproject/dropwizard-service-utilities/issues/289">289</a>
     * @link <a href="https://github.com/kiwiproject/dropwizard-service-utilities/issues/290">290</a>
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CacheConfig {

        private Duration watchDuration;
        private Duration minBackOffDelay;
        private Duration maxBackOffDelay;

        // other properties...

        // problematic property
        private RefreshErrorLogConsumer errorLogConsumer;

        // problematic interface; causes empty value in JSON, e.g. { "errorLogConsumer": }
        interface RefreshErrorLogConsumer {
            @SuppressWarnings("unused")
            void accept(Logger logger, String message, Throwable error);
        }
    }

    public static class TestApp extends Application<TestConfig> {

        public TestApp() {
        }

        @Override
        public void run(TestConfig config, Environment environment) {
            environment.jersey().register(new ConfigResource(config, List.of(".*Password")));
        }
    }

    private static final TestConfig CONFIG = new TestConfig("foo", "secret",
            new CacheConfig(Duration.ofSeconds(10), Duration.ofSeconds(5), Duration.ofSeconds(25),
                    (logger, message, error) -> {
                        // do nothing...doesn't matter for this test
                    }));
    private static final DropwizardAppExtension<TestConfig> APP = new DropwizardAppExtension<>(TestApp.class, CONFIG);

    @Test
    void shouldReturnConfigAndMaskSecrets() {
        var configData = getAppConfig();

        assertThat(configData)
                .containsEntry("someNeededProperty", "foo")
                .containsEntry("someHiddenPassword", "********")
                .containsKey("cacheConfig");
    }

    @ClearBoxTest("proves TestConfig does not serialize when FAIL_ON_EMPTY_BEANS is enabled")
    @Issue("290")
    void shouldThrowExceptionConvertingToJsonWhenFailOnEmptyBeansIsEnabled() {
        assertThatThrownBy(() -> JSON_HELPER.toJson(CONFIG))
                .isInstanceOf(RuntimeJsonException.class)
                .hasCauseInstanceOf(InvalidDefinitionException.class)
                .hasMessageContaining("No serializer found")
                .hasMessageContaining("to avoid exception, disable SerializationFeature.FAIL_ON_EMPTY_BEANS");
    }

    @Test
    @Issue("290")
    void shouldHandleNonSerializableBeans() {
        var configData = getAppConfig();

        assertThat(configData).containsKey("cacheConfig");
        var cacheConfig = configData.get("cacheConfig");
        var cacheConfigMap = KiwiAssertJ.assertIsTypeOrSubtype(cacheConfig, Map.class);

        // noinspection unchecked
        assertThat(cacheConfigMap)
                .describedAs("empty non-serializable bean should be an empty map")
                .containsEntry("errorLogConsumer", Map.of());
    }

    private static Map<String, Object> getAppConfig() {
        var response = APP.client().target("http://localhost:" + APP.getLocalPort() + "/app/config")
                .request()
                .get();

        return response.readEntity(MAP_OF_STRING_TO_OBJECT_GENERIC_TYPE);
    }
}
