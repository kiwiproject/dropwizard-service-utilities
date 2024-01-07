package org.kiwiproject.dropwizard.util.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.core.server.ServerFactory;
import io.dropwizard.core.setup.Environment;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.jaxrs.exception.ConstraintViolationExceptionMapper;
import org.kiwiproject.jaxrs.exception.IllegalArgumentExceptionMapper;
import org.kiwiproject.jaxrs.exception.IllegalStateExceptionMapper;
import org.kiwiproject.jaxrs.exception.JaxrsExceptionMapper;
import org.kiwiproject.jaxrs.exception.NoSuchElementExceptionMapper;
import org.kiwiproject.jaxrs.exception.WebApplicationExceptionMapper;
import org.kiwiproject.test.dropwizard.mockito.DropwizardMockitoMocks;

import java.lang.invoke.MethodHandle;

@DisplayName("StandardExceptionMappers")
class StandardExceptionMappersTest {

    @Nested
    class Register {

        @Test
        void shouldRegisterExceptionMappers() {
            var serverFactory = new DefaultServerFactory() {
                @Override
                public void setRegisterDefaultExceptionMappers(Boolean registerDefaultExceptionMappers) {
                    assertThat(registerDefaultExceptionMappers).isFalse();
                }
            };

            var env = DropwizardMockitoMocks.mockEnvironment();

            StandardExceptionMappers.register(serverFactory, env);

            var jersey = env.jersey();
            verify(jersey).register(isA(WebApplicationExceptionMapper.class));
            verify(jersey).register(isA(IllegalArgumentExceptionMapper.class));
            verify(jersey).register(isA(IllegalStateExceptionMapper.class));
            verify(jersey).register(isA(NoSuchElementExceptionMapper.class));
            verify(jersey).register(isA(JaxrsExceptionMapper.class));

            verify(jersey).register(any(LoggingExceptionMapper.class));
            verify(jersey).register(isA(JsonProcessingExceptionMapper.class));
            verify(jersey).register(isA(EmptyOptionalExceptionMapper.class));
            verify(jersey).register(isA(EarlyEofExceptionMapper.class));
            verify(jersey).register(isA(ConstraintViolationExceptionMapper.class));
            verify(jersey).register(isA(JerseyViolationExceptionMapper.class));

            verifyNoMoreInteractions(jersey);
        }
    }

    @Nested
    class FindRegistrationSetter {

        @Test
        void shouldThrowIllegalStateException_WhenServerFactoryHasInvalidType() {
            var serverFactory = new ServerFactory() {
                @Override
                public Server build(Environment environment) {
                    return null;
                }

                @Override
                public void configure(Environment environment) {
                    // intentionally does nothing
                }
            };
            assertThatThrownBy(() -> StandardExceptionMappers.findRegistrationSetter(serverFactory))
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("ServerFactory class")
                    .hasMessageEndingWith("must respond to 'setRegisterDefaultExceptionMappers' to disable default exception mapper registration!");
        }

        @Test
        void shouldNotThrowExceptions_WhenServerFactoryHasValidType() {
            var serverFactory = new DefaultServerFactory() {
                @Override
                public void setRegisterDefaultExceptionMappers(Boolean registerDefaultExceptionMappers) {
                    assertThat(registerDefaultExceptionMappers).isFalse();
                }
            };

            assertThatCode(() -> StandardExceptionMappers.findRegistrationSetter(serverFactory))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class InvokeRegistrationSetter {

        @Test
        void shouldThrowIllegalStateException_IfErrorInvoking() {
            var serverFactory = new DefaultServerFactory() {
                @Override
                public void setRegisterDefaultExceptionMappers(Boolean registerDefaultExceptionMappers) {
                    throw new RuntimeException("oops");
                }
            };

            MethodHandle registrationSetter = StandardExceptionMappers.findRegistrationSetter(serverFactory);
            assertThatThrownBy(() -> StandardExceptionMappers.invokeRegistrationSetter(registrationSetter, serverFactory))
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("Unable to invoke 'setRegisterDefaultExceptionMappers' using handle")
                    .hasMessageEndingWith("Cannot disable default exception mapper registration!");
        }

        @Test
        void shouldNotThrowExceptions_WhenSetterIsCalledWithoutError() {
            var serverFactory = new DefaultServerFactory() {
                @Override
                public void setRegisterDefaultExceptionMappers(Boolean registerDefaultExceptionMappers) {
                    assertThat(registerDefaultExceptionMappers).isFalse();
                }
            };

            MethodHandle registrationSetter = StandardExceptionMappers.findRegistrationSetter(serverFactory);
            assertThatCode(() -> StandardExceptionMappers.invokeRegistrationSetter(registrationSetter, serverFactory))
                    .doesNotThrowAnyException();
        }
    }
}
