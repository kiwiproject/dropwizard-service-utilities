package org.kiwiproject.dropwizard.util.exception;

import static java.lang.invoke.MethodType.methodType;
import static org.kiwiproject.base.KiwiStrings.format;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.core.server.AbstractServerFactory;
import io.dropwizard.core.server.ServerFactory;
import io.dropwizard.core.setup.Environment;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.jaxrs.exception.ConstraintViolationExceptionMapper;
import org.kiwiproject.jaxrs.exception.IllegalArgumentExceptionMapper;
import org.kiwiproject.jaxrs.exception.IllegalStateExceptionMapper;
import org.kiwiproject.jaxrs.exception.JaxrsExceptionMapper;
import org.kiwiproject.jaxrs.exception.NoSuchElementExceptionMapper;
import org.kiwiproject.jaxrs.exception.WebApplicationExceptionMapper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * Utility to configure an opinionated "standard" set of exception mappers.
 */
@UtilityClass
@Slf4j
public class StandardExceptionMappers {

    private static final String REGISTER_DEFAULT_EXCEPTION_MAPPERS_SETTER = "setRegisterDefaultExceptionMappers";

    /**
     * Registers the "standard" set of exception mappers.
     * <p>
     * This uses {@link #disableDefaultExceptionMapperRegistration(ServerFactory)} to prevent
     * Dropwizard from registering any of its exception mappers.
     *
     * @param serverFactory the serverFactory so that the default exception mappers can be disabled
     * @param environment   the Dropwizard environment
     * @see #disableDefaultExceptionMapperRegistration(ServerFactory)
     */
    public static void register(ServerFactory serverFactory, Environment environment) {
        var jersey = environment.jersey();

        // Register our custom exception mappers
        jersey.register(new WebApplicationExceptionMapper());
        jersey.register(new IllegalArgumentExceptionMapper());
        jersey.register(new IllegalStateExceptionMapper());
        jersey.register(new NoSuchElementExceptionMapper());
        jersey.register(new JaxrsExceptionMapper());

        // Don't allow Dropwizard to register default exception mappers, since we are overriding
        // its default exception mappers with replacements from kiwi
        disableDefaultExceptionMapperRegistration(serverFactory);

        // Register replacements for Dropwizard default exception mappers
        jersey.register(new LoggingExceptionMapper<>() {});
        jersey.register(new JsonProcessingExceptionMapper());
        jersey.register(new EmptyOptionalExceptionMapper());
        jersey.register(new EarlyEofExceptionMapper());
        jersey.register(new ConstraintViolationExceptionMapper());
        jersey.register(new JerseyViolationExceptionMapper());
    }

    /**
     * Disable registration of Dropwizard exception mappers if the {@link ServerFactory} supports it
     * via a {@code setRegisterDefaultExceptionMappers} method which accepts a {@link Boolean} (the
     * wrapper type, not a primitive {@code boolean}).
     * <p>
     * Both Dropwizard implementations, {@link io.dropwizard.core.server.DefaultServerFactory DefaultServerFactory}
     * and {@link io.dropwizard.core.server.SimpleServerFactory SimpleServerFactory}, support this option
     * since they extend {@link io.dropwizard.core.server.AbstractServerFactory AbstractServerFactory}.
     * <p>
     * This should only be used if you do not want any of Dropwizard's default exception mappers to be
     * registered. The {@link io.dropwizard.core.setup.ExceptionMapperBinder ExceptionMapperBinder} registers
     * Dropwizard's default set of exception mappers.
     * <p>
     * Also see
     * <a href="https://www.dropwizard.io/en/stable/manual/core.html#overriding-default-exception-mappers">
     * Overriding Default Exception Mappers
     * </a>
     * in the Dropwizard reference manual.
     *
     * @param serverFactory
     * @see io.dropwizard.core.setup.ExceptionMapperBinder
     * @see io.dropwizard.core.server.AbstractServerFactory#setRegisterDefaultExceptionMappers(Boolean)
     */
    public static void disableDefaultExceptionMapperRegistration(ServerFactory serverFactory) {
        LOG.info("Disabling Dropwizard registration of default exception mappers");
        if (serverFactory instanceof AbstractServerFactory baseFactory) {
            baseFactory.setRegisterDefaultExceptionMappers(false);
        } else {
            var methodHandle = findRegistrationSetter(serverFactory);
            invokeRegistrationSetter(methodHandle, serverFactory);
        }
    }

    @VisibleForTesting
    static MethodHandle findRegistrationSetter(ServerFactory serverFactory) {
        try {
            return MethodHandles.lookup().findVirtual(serverFactory.getClass(),
                    REGISTER_DEFAULT_EXCEPTION_MAPPERS_SETTER, methodType(Void.TYPE, Boolean.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new IllegalStateException(
                    format("ServerFactory class ({}) must respond to '{}(Boolean)' to disable default exception mapper registration!",
                            serverFactory.getClass(), REGISTER_DEFAULT_EXCEPTION_MAPPERS_SETTER),
                    ex);
        }
    }

    @VisibleForTesting
    static void invokeRegistrationSetter(MethodHandle methodHandle, ServerFactory serverFactory) {
        try {
            methodHandle.invoke(serverFactory, Boolean.FALSE);
        } catch (Throwable throwable) {
            throw new IllegalStateException(
                    format("Unable to invoke '{}(Boolean.FALSE)' using handle {} on {}. Cannot disable default exception mapper registration!",
                            REGISTER_DEFAULT_EXCEPTION_MAPPERS_SETTER, methodHandle, serverFactory),
                    throwable);
        }
    }
}
