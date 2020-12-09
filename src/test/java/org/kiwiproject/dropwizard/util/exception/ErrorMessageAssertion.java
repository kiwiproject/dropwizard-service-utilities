package org.kiwiproject.dropwizard.util.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.collect.KiwiLists.first;

import org.kiwiproject.jaxrs.exception.ErrorMessage;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

public class ErrorMessageAssertion {

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
