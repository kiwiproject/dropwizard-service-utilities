package org.kiwiproject.dropwizard.util.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.collect.KiwiLists.first;

import jakarta.ws.rs.core.Response;
import lombok.experimental.UtilityClass;
import org.kiwiproject.jaxrs.exception.ErrorMessage;

import java.util.List;
import java.util.Map;

@UtilityClass
public class ErrorMessageAssertions {

    @SuppressWarnings({"unchecked", "rawtypes"})
    static ErrorMessage assertAndGetErrorMessage(Response r) {
        assertThat(r.getEntity()).isInstanceOf(Map.class);
        var entity = (Map) r.getEntity();

        assertThat(entity).containsKey("errors");
        var errorsObj = entity.get("errors");

        assertThat(errorsObj).isInstanceOf(List.class);
        var errors = (List<ErrorMessage>) entity.get("errors");

        return first(errors);
    }
}
