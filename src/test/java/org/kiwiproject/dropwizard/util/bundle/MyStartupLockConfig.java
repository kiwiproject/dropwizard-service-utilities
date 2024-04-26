package org.kiwiproject.dropwizard.util.bundle;

import io.dropwizard.core.Configuration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.With;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MyStartupLockConfig extends Configuration {

    @With
    private boolean useDynamicPorts = true;

    private String zkStartupLockPath = "/kiwi/service/startup-locks";
}
