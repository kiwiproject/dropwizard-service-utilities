package org.kiwiproject.dropwizard.util.bundle;

import lombok.experimental.UtilityClass;

import org.kiwiproject.dropwizard.util.startup.AllowablePortRange;
import org.kiwiproject.dropwizard.util.startup.PortAssigner;

@UtilityClass
class PortAssigners {

    static PortAssigner.PortAssignment portAssignmentFrom(DynamicPortsConfiguration dynamicPortsConfig) {
        return dynamicPortsConfig.isUseDynamicPorts() ?
                PortAssigner.PortAssignment.DYNAMIC : PortAssigner.PortAssignment.STATIC;
    }

    static PortAssigner.PortAssignment portAssignmentFrom(StartupLockConfiguration startupLockConfig) {
        return startupLockConfig.isUseDynamicPorts() ?
                PortAssigner.PortAssignment.DYNAMIC : PortAssigner.PortAssignment.STATIC;
    }

    static AllowablePortRange allowablePortRangeFrom(DynamicPortsConfiguration dynamicPortsConfig) {
        return new AllowablePortRange(dynamicPortsConfig.getMinDynamicPort(), dynamicPortsConfig.getMaxDynamicPort());
    }

    static PortAssigner.PortSecurity portSecurityFrom(DynamicPortsConfiguration dynamicPortsConfig) {
        return dynamicPortsConfig.isUseSecureDynamicPorts() ?
                PortAssigner.PortSecurity.SECURE : PortAssigner.PortSecurity.NON_SECURE;
    }
}
