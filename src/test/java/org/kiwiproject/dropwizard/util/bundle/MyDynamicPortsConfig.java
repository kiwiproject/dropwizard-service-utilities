package org.kiwiproject.dropwizard.util.bundle;

import io.dropwizard.core.Configuration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.With;
import org.kiwiproject.dropwizard.util.startup.FreePortFinder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MyDynamicPortsConfig extends Configuration {

    @With
    private boolean useDynamicPorts = true;

    @With
    private FreePortFinder freePortFinder;
}
