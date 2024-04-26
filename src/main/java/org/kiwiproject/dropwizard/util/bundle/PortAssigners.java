package org.kiwiproject.dropwizard.util.bundle;

import lombok.experimental.UtilityClass;

import org.kiwiproject.dropwizard.util.startup.AllowablePortRange;
import org.kiwiproject.dropwizard.util.startup.PortAssigner;
import org.kiwiproject.dropwizard.util.startup.PortAssigner.PortAssignment;
import org.kiwiproject.dropwizard.util.startup.PortAssigner.PortSecurity;

@UtilityClass
class PortAssigners {

    static PortAssignment portAssignmentFrom(DynamicPortsConfiguration dynamicPortsConfig) {
        return PortAssignment.fromBooleanDynamicWhenTrue(dynamicPortsConfig.isUseDynamicPorts());
    }

    static PortAssignment portAssignmentFrom(StartupLockConfiguration startupLockConfig) {
        return PortAssignment.fromBooleanDynamicWhenTrue(startupLockConfig.isUseDynamicPorts());
    }

    static AllowablePortRange allowablePortRangeFrom(DynamicPortsConfiguration dynamicPortsConfig) {
        return new AllowablePortRange(dynamicPortsConfig.getMinDynamicPort(), dynamicPortsConfig.getMaxDynamicPort());
    }

    static PortAssigner.PortSecurity portSecurityFrom(DynamicPortsConfiguration dynamicPortsConfig) {
        return PortSecurity.fromBooleanSecureWhenTrue(dynamicPortsConfig.isUseSecureDynamicPorts());
    }
}
