package org.kiwiproject.dropwizard.util.exception;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.dropwizard.core.server.AbstractServerFactory;
import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.core.server.ServerFactory;
import io.dropwizard.core.server.SimpleServerFactory;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.jersey.setup.JerseyEnvironment;
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
            verify(jersey).register(isA(RuntimeJsonExceptionMapper.class));

            // The base (anonymous) LoggingExceptionMapper, not SQL or JDBI subclasses
            verify(jersey).register(argThat((Object m) ->
                    m instanceof LoggingExceptionMapper &&
                    !(m instanceof LoggingSQLExceptionMapper) &&
                    !(m instanceof LoggingJdbiExceptionMapper)));
            verify(jersey).register(isA(JsonProcessingExceptionMapper.class));
            verify(jersey).register(isA(EmptyOptionalExceptionMapper.class));
            verify(jersey).register(isA(EarlyEofExceptionMapper.class));
            verify(jersey).register(isA(ConstraintViolationExceptionMapper.class));
            verify(jersey).register(isA(JerseyViolationExceptionMapper.class));

            // JDBI3 is on the test classpath, so these mappers are also registered
            verify(jersey).register(isA(LoggingSQLExceptionMapper.class));
            verify(jersey).register(isA(LoggingJdbiExceptionMapper.class));

            verifyNoMoreInteractions(jersey);
        }
    }

    @Nested
    class RegisterJdbi3ExceptionMappers {

        @Test
        void shouldRegisterSQLAndJdbiExceptionMappers() {
            var jersey = mock(JerseyEnvironment.class);

            StandardExceptionMappers.registerJdbi3ExceptionMappers(jersey);

            verify(jersey).register(isA(LoggingSQLExceptionMapper.class));
            verify(jersey).register(isA(LoggingJdbiExceptionMapper.class));
            verifyNoMoreInteractions(jersey);
        }
    }

    @Nested
    class IsJdbi3Available {

        @Test
        void shouldReturnTrue_WhenJdbi3IsOnClasspath() {
            assertThat(StandardExceptionMappers.isJdbi3Available()).isTrue();
        }

        @Test
        void shouldReturnFalse_WhenJdbi3ISNotOnClasspath() {
            assertThat(StandardExceptionMappers.isJdbi3Available(jdbi3HidingClassLoader())).isFalse();
        }

        private static ClassLoader jdbi3HidingClassLoader() {
            return new ClassLoader(StandardExceptionMappers.class.getClassLoader()) {
                @Override
                public Class<?> loadClass(String name) throws ClassNotFoundException {
                    if (name.startsWith("org.jdbi")) {
                        throw new ClassNotFoundException(name);
                    }
                    return super.loadClass(name);
                }
            };
        }
    }

    @Nested
    class DisableDefaultExceptionMapperRegistration {

        @Test
        void shouldDisableForDefaultServerFactory() {
            var serverFactory = checkAbstractServerFactoryPrecondition(new DefaultServerFactory());

            StandardExceptionMappers.disableDefaultExceptionMapperRegistration(serverFactory);

            assertThat(serverFactory.getRegisterDefaultExceptionMappers()).isFalse();
        }

        @Test
        void shouldDisableForSimpleServerFactory() {
            var serverFactory = checkAbstractServerFactoryPrecondition(new SimpleServerFactory());

            StandardExceptionMappers.disableDefaultExceptionMapperRegistration(serverFactory);

            assertThat(serverFactory.getRegisterDefaultExceptionMappers()).isFalse();
        }

        private static AbstractServerFactory checkAbstractServerFactoryPrecondition(AbstractServerFactory serverFactory) {
            assertThat(serverFactory.getRegisterDefaultExceptionMappers())
                    .describedAs("precondition failed: expected registerDefaultExceptionMappers=true")
                    .isTrue();

            return serverFactory;
        }

        @Test
        void shouldDisableForCustomServerFactory_WhichSupportsDisabling() {
            var serverFactory = new SupportedCustomServerFactory();

            StandardExceptionMappers.disableDefaultExceptionMapperRegistration(serverFactory);

            assertAll(
                () -> assertThat(serverFactory.registerDefaultExceptionMappersCalled).isTrue(),
                () -> assertThat(serverFactory.argumentHadCorrectValue).isTrue()
            );
        }

        @Test
        void shouldThrowIllegalState_IfServerFactoryDoesNotSupportDisabling() {
            var serverFactory = new UnsupportedCustomServerFactory();

            assertThatIllegalStateException()
                    .isThrownBy(() -> StandardExceptionMappers.disableDefaultExceptionMapperRegistration(serverFactory));
        }

        @Test
        void shouldThrowIllegalState_IfServerFactoryHasCorrectlyNamedMethodThatAcceptsPrimitiveBoolean() {
            var serverFactory = new UnsupportedPrimitiveBooleanCustomServerFactory();

            assertThatIllegalStateException()
                    .isThrownBy(() -> StandardExceptionMappers.disableDefaultExceptionMapperRegistration(serverFactory));
        }
    }

    public static class SupportedCustomServerFactory implements ServerFactory {

        boolean registerDefaultExceptionMappersCalled;
        boolean argumentHadCorrectValue;

        @SuppressWarnings("unused")  // this is called via reflection
        public void setRegisterDefaultExceptionMappers(Boolean registerDefaultExceptionMappers) {
            registerDefaultExceptionMappersCalled = true;
            argumentHadCorrectValue = nonNull(registerDefaultExceptionMappers) && !registerDefaultExceptionMappers;
        }

        @Override
        public Server build(Environment environment) {
            throw new UnsupportedOperationException("Should never be called by tests");
        }

        @Override
        public void configure(Environment environment) {
            throw new UnsupportedOperationException("Should never be called by tests");
        }
    }

    public static class UnsupportedCustomServerFactory implements ServerFactory {

        @Override
        public Server build(Environment environment) {
            throw new UnsupportedOperationException("Should never be called by tests");
        }

        @Override
        public void configure(Environment environment) {
            throw new UnsupportedOperationException("Should never be called by tests");
        }
    }

    public static class UnsupportedPrimitiveBooleanCustomServerFactory implements ServerFactory {

        boolean registerDefaultExceptionMappersCalled;

        @SuppressWarnings("unused")  // there is nothing to do with the parameter, but it must exist
        public void setRegisterDefaultExceptionMappers(boolean registerDefaultExceptionMappers) {
            registerDefaultExceptionMappersCalled = true;
        }

        @Override
        public Server build(Environment environment) {
            throw new UnsupportedOperationException("Should never be called by tests");
        }

        @Override
        public void configure(Environment environment) {
            throw new UnsupportedOperationException("Should never be called by tests");
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
                    .hasMessageEndingWith("must respond to 'setRegisterDefaultExceptionMappers(Boolean)' to disable default exception mapper registration!");
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
                    .hasMessageStartingWith("Unable to invoke 'setRegisterDefaultExceptionMappers(Boolean.FALSE)' using handle")
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
