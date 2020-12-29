package org.kiwiproject.dropwizard.util.resource;

import static org.assertj.core.api.Assertions.assertThat;

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.core.GenericType;
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
    }

    public static class TestApp extends Application<TestConfig> {

        public TestApp() {}

        @Override
        public void run(TestConfig config, Environment environment) {
            environment.jersey().register(new ConfigResource(config, List.of(".*Password")));
        }
    }

    private static final TestConfig CONFIG = new TestConfig("foo", "secret");
    private static final DropwizardAppExtension<TestConfig> APP = new DropwizardAppExtension<>(TestApp.class, CONFIG);

    @Test
    void shouldReturnConfigAndMaskSecrets() {
        var response = APP.client().target("http://localhost:" + APP.getLocalPort() + "/app/config")
                .request()
                .get();

        var configData = response.readEntity(new GenericType<Map<String, Object>>(){});

        assertThat(configData)
                .containsEntry("someNeededProperty", "foo")
                .containsEntry("someHiddenPassword", "********");
    }
}
