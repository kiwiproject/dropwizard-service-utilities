package org.kiwiproject.dropwizard.util.bundle;

import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

public class MyDynamicPortsApp extends Application<MyDynamicPortsConfig> {

    public static final int MIN_DYNAMIC_PORT = 15_000;
    public static final int MAX_DYNAMIC_PORT = 25_000;

    @Override
    public void initialize(Bootstrap<MyDynamicPortsConfig> bootstrap) {
        var dynamicPortsBundle = new DynamicPortsBundle<MyDynamicPortsConfig>() {
            @Override
            public DynamicPortsConfiguration getDynamicPortsConfiguration(MyDynamicPortsConfig configuration) {
                return DynamicPortsConfiguration.builder()
                    .useDynamicPorts(configuration.isUseDynamicPorts())
                    .useSecureDynamicPorts(false)
                    .minDynamicPort(MIN_DYNAMIC_PORT)
                    .maxDynamicPort(MAX_DYNAMIC_PORT)
                    .freePortFinder(configuration.getFreePortFinder())
                    .build();
            }
        };

        bootstrap.addBundle(dynamicPortsBundle);
    }

    @Override
    public void run(MyDynamicPortsConfig configuration, Environment environment) {
        // intentionally empty
    }
}
