package org.kiwiproject.dropwizard.util.config;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
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
