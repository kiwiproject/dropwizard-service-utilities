package org.kiwiproject.dropwizard.util.config;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class JacksonConfig {

    @Builder.Default
    private final boolean ignoreButWarnForUnknownJsonProperties = true;

    @Builder.Default
    private final boolean registerHealthCheckForUnknownJsonProperties = true;

    @Builder.Default
    private final boolean readAndWriteDateTimestampsAsMillis = true;

    @Builder.Default
    private final boolean writeNilJaxbElementsAsNull = true;

}
