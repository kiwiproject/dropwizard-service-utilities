package org.kiwiproject.dropwizard.util.job;

import static java.util.Objects.nonNull;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;

import org.jspecify.annotations.Nullable;
import org.kiwiproject.base.KiwiThrowables;

/**
 * Contains basic metadata about an Exception that occurred during job execution.
 *
 * @param type             the type of Exception, may not be blank
 * @param message          the message from the Exception, may be null
 * @param rootCauseType    the type of the root cause Exception, may be null
 * @param rootCauseMessage the message from the root cause Exception, may be null
 */
public record JobExceptionInfo(
        String type,
        @Nullable String message,
        @Nullable String rootCauseType,
        @Nullable String rootCauseMessage) {
    public JobExceptionInfo {
        checkArgumentNotBlank(type, "type must not be blank");
    }

    /**
     * Creates a JobExceptionInfo object from the given Exception.
     *
     * @param e the Exception from which to create the JobExceptionInfo
     * @return a new instance
     */
    public static JobExceptionInfo from(Exception e) {
        checkArgumentNotNull(e, "exception must not be null");

        var rootCause = KiwiThrowables.rootCauseOf(e).orElse(null);

        String rootCauseType = null;
        String rootCauseMessage = null;
        if (nonNull(rootCause) && rootCause != e) {
            rootCauseType = KiwiThrowables.typeOf(rootCause);
            rootCauseMessage = KiwiThrowables.messageOf(rootCause).orElse(null);
        }

        return new JobExceptionInfo(
                KiwiThrowables.typeOf(e),
                KiwiThrowables.messageOf(e).orElse(null),
                rootCauseType,
                rootCauseMessage);
    }
}
