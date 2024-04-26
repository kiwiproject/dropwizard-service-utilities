package org.kiwiproject.dropwizard.util.bundle;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.With;

import io.dropwizard.core.Configuration;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MyDynamicPortsConfig extends Configuration {

    @With
    private boolean useDynamicPorts = true;
}
