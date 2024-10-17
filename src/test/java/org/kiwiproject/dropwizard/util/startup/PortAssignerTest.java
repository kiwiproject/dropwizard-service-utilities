package org.kiwiproject.dropwizard.util.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.kiwiproject.collect.KiwiLists.first;
import static org.kiwiproject.collect.KiwiLists.second;
import static org.kiwiproject.dropwizard.util.startup.PortAssigner.PortAssignment.DYNAMIC;
import static org.kiwiproject.dropwizard.util.startup.PortAssigner.PortAssignment.STATIC;
import static org.kiwiproject.dropwizard.util.startup.PortAssigner.PortSecurity.NON_SECURE;
import static org.kiwiproject.dropwizard.util.startup.PortAssigner.PortSecurity.SECURE;
import static org.kiwiproject.test.assertj.KiwiAssertJ.assertIsExactType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.core.server.SimpleServerFactory;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.jetty.HttpsConnectorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kiwiproject.config.TlsContextConfiguration;
import org.kiwiproject.dropwizard.util.exception.NoAvailablePortException;
import org.kiwiproject.dropwizard.util.startup.FreePortFinder.ServicePorts;
import org.kiwiproject.dropwizard.util.startup.PortAssigner.PortAssignment;
import org.kiwiproject.dropwizard.util.startup.PortAssigner.PortSecurity;
import org.kiwiproject.registry.model.Port;

import java.util.List;

@DisplayName("PortAssigner")
class PortAssignerTest {

    @Nested
    class Builder {

        @Nested
        class PortSecurityField {

            @Test
            void shouldDefaultToSecure() {
                var assigner = PortAssigner.builder()
                        .tlsConfiguration(TlsContextConfiguration.builder().build())
                        .serverFactory(new DefaultServerFactory())
                        .build();

                assertThat(assigner.getPortSecurity()).isEqualTo(SECURE);

            }

            @Test
            void shouldUseProvided() {
                var assigner = PortAssigner.builder()
                        .portSecurity(NON_SECURE)
                        .serverFactory(new DefaultServerFactory())
                        .build();

                assertThat(assigner.getPortSecurity()).isEqualTo(NON_SECURE);
            }
        }

        @Nested
        class TlsProviderField {

            @Test
            void shouldThrowIllegalStateException_WhenSecureAndMissing() {
                var portAssignerBuilder = PortAssigner.builder();

                assertThatThrownBy(portAssignerBuilder::build)
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            void shouldNotBeRequired_WhenNonSecure() {
                var assigner = PortAssigner.builder()
                        .portSecurity(NON_SECURE)
                        .serverFactory(new DefaultServerFactory())
                        .build();

                assertThat(assigner.getTlsConfiguration()).isNull();
            }

            @Test
            void shouldUseProvided_WhenSecure() {
                var tlsConfig = TlsContextConfiguration.builder().build();
                var assigner = PortAssigner.builder()
                        .tlsConfiguration(tlsConfig)
                        .serverFactory(new DefaultServerFactory())
                        .build();

                assertThat(assigner.getTlsConfiguration()).isSameAs(tlsConfig);
            }
        }

        @Nested
        class PortAssignmentField {

            @Test
            void shouldDefaultToDynamic() {
                var assigner = PortAssigner.builder()
                        .portSecurity(NON_SECURE)
                        .serverFactory(new DefaultServerFactory())
                        .build();

                assertThat(assigner.getPortAssignment()).isEqualTo(DYNAMIC);
            }

            @Test
            void shouldUseProvided() {
                var assigner = PortAssigner.builder()
                        .portSecurity(NON_SECURE)
                        .portAssignment(STATIC)
                        .serverFactory(new DefaultServerFactory())
                        .build();

                assertThat(assigner.getPortAssignment()).isEqualTo(STATIC);
            }
        }

        @Nested
        class FreePortFinderField {

            @Test
            void shouldDefaultToRandomFreePortFinder() {
                var assigner = PortAssigner.builder()
                        .portSecurity(NON_SECURE)
                        .serverFactory(new DefaultServerFactory())
                        .build();

                assertThat(assigner.getFreePortFinder())
                        .isExactlyInstanceOf(RandomFreePortFinder.class);
            }
        }

        @Nested
        class AllowablePortRangeField {

            @Test
            void shouldDefaultToNull() {
                var assigner = PortAssigner.builder()
                        .portSecurity(NON_SECURE)
                        .serverFactory(new DefaultServerFactory())
                        .build();

                assertThat(assigner.getAllowablePortRange()).isNull();
            }

            @Test
            void shouldUseProvided() {
                var range = new AllowablePortRange(1, 10);

                var assigner = PortAssigner.builder()
                        .portSecurity(NON_SECURE)
                        .allowablePortRange(range)
                        .serverFactory(new DefaultServerFactory())
                        .build();

                assertThat(assigner.getAllowablePortRange()).isSameAs(range);
            }
        }

        @Nested
        class ServerFactoryField {

            @Test
            void shouldThrowIllegalArgumentException_WhenMissing() {
                var portAssignerBuilder = PortAssigner.builder()
                        .portSecurity(NON_SECURE);

                assertThatThrownBy(portAssignerBuilder::build)
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            void shouldThrowIllegalArgumentException_WhenNotDefaultServerFactory() {
                var portAssignerBuilder = PortAssigner.builder()
                        .portSecurity(NON_SECURE)
                        .serverFactory(new SimpleServerFactory());

                assertThatThrownBy(portAssignerBuilder::build)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageStartingWith("The server factory is not a %s (it is a ", DefaultServerFactory.class.getName())
                        .hasMessageEndingWith("SimpleServerFactory)");
            }

            @Test
            void shouldUseProvided() {
                var factory = new DefaultServerFactory();

                var assigner = PortAssigner.builder()
                        .portSecurity(NON_SECURE)
                        .serverFactory(factory)
                        .build();

                assertThat(assigner.getServerFactory()).isSameAs(factory);
            }
        }
    }

    @Nested
    class AssignDynamicPorts {

        @Test
        void shouldNotChangeConnectors_WhenStaticPortAssignment() {
            var factory = new DefaultServerFactory();
            var defaultAppConnector = first(factory.getApplicationConnectors());
            var defaultAdminConnector = first(factory.getAdminConnectors());

            var originalApplicationPort = getPort(defaultAppConnector);
            var originalAdminPort = getPort(defaultAdminConnector);

            var assigner = PortAssigner.builder()
                    .portAssignment(STATIC)
                    .serverFactory(factory)
                    .portSecurity(NON_SECURE)
                    .build();

            var ports = assigner.assignDynamicPorts();
            assertThat(first(ports).getNumber()).isEqualTo(originalApplicationPort);
            assertThat(second(ports).getNumber()).isEqualTo(originalAdminPort);

            assertThat(factory.getApplicationConnectors()).containsOnly(defaultAppConnector);
            assertThat(factory.getAdminConnectors()).containsOnly(defaultAdminConnector);
        }

        @Test
        void shouldSetupSecureDynamicPorts_WhenSecureSpecified() {
            var factory = new DefaultServerFactory();
            var tlsConfig = TlsContextConfiguration.builder().build();
            var minPortNumber = 9_000;
            var maxPortNumber = 9_100;
            var allowedRange = new AllowablePortRange(minPortNumber, maxPortNumber);

            var assigner = PortAssigner.builder()
                    .portAssignment(DYNAMIC)
                    .serverFactory(factory)
                    .portSecurity(SECURE)
                    .tlsConfiguration(tlsConfig)
                    .allowablePortRange(allowedRange)
                    .build();

            var originalApplicationPort = firstApplicationPort(factory);
            var originalAdminPort = firstAdminPort(factory);

            var ports = assigner.assignDynamicPorts();

            assertThat(first(ports).getNumber())
                    .isNotEqualTo(originalApplicationPort)
                    .isBetween(minPortNumber, maxPortNumber);

            assertThat(second(ports).getNumber())
                    .isNotEqualTo(originalAdminPort)
                    .isBetween(minPortNumber, maxPortNumber);

            var applicationConnector = assertIsExactType(first(factory.getApplicationConnectors()), HttpsConnectorFactory.class);
            assertThat(applicationConnector.getPort()).isBetween(minPortNumber, maxPortNumber);

            var adminConnector = assertIsExactType(first(factory.getAdminConnectors()), HttpsConnectorFactory.class);
            assertThat(adminConnector.getPort()).isBetween(minPortNumber, maxPortNumber);
        }

        @Test
        void shouldSetupSecureDynamicPorts_AsZero_WhenPortRangeExcluded() {
            var factory = new DefaultServerFactory();
            var tlsConfig = TlsContextConfiguration.builder().build();

            var assigner = PortAssigner.builder()
                    .portAssignment(DYNAMIC)
                    .serverFactory(factory)
                    .portSecurity(SECURE)
                    .tlsConfiguration(tlsConfig)
                    .build();

            var ports = assigner.assignDynamicPorts();

            assertThat(first(ports).getNumber()).isZero();
            assertThat(second(ports).getNumber()).isZero();

            var applicationConnector = assertIsExactType(first(factory.getApplicationConnectors()), HttpsConnectorFactory.class);
            assertThat(applicationConnector.getPort()).isZero();

            var adminConnector = assertIsExactType(first(factory.getAdminConnectors()), HttpsConnectorFactory.class);
            assertThat(adminConnector.getPort()).isZero();
        }

        @Test
        void shouldSetSecureProperties_OnApplicationConnector_WhenAssigningSecureDynamicPorts() {
            var factory = new DefaultServerFactory();
            var tlsConfig = TlsContextConfiguration.builder()
                    .keyStorePath("/data/etc/pki/acme-ks.jks")
                    .keyStorePassword("R3ally-hArd-passw0rD")
                    .trustStorePath("/data/etc/pki/acme-ts.jks")
                    .supportedProtocols(List.of("TLSv1.2", "TLSv1.3"))
                    .disableSniHostCheck(true)
                    .build();

            var assigner = PortAssigner.builder()
                    .portAssignment(DYNAMIC)
                    .serverFactory(factory)
                    .portSecurity(SECURE)
                    .tlsConfiguration(tlsConfig)
                    .build();

            var ports = assigner.assignDynamicPorts();
            assertThat(ports).hasSize(2);

            var applicationConnector = assertIsExactType(first(factory.getApplicationConnectors()), HttpsConnectorFactory.class);

            assertSecureConnectorProperties(applicationConnector, tlsConfig);
        }

        @Test
        void shouldSetSecureProperties_OnAdminConnector_WhenAssigningSecureDynamicPorts() {
            var factory = new DefaultServerFactory();
            var tlsConfig = TlsContextConfiguration.builder()
                    .keyStorePath("/data/etc/pki/acme-ks.jks")
                    .keyStorePassword("R3ally-hArd-passw0rD")
                    .trustStorePath("/data/etc/pki/acme-ts.jks")
                    .supportedProtocols(List.of("TLSv1.2", "TLSv1.3"))
                    .disableSniHostCheck(true)
                    .build();

            var assigner = PortAssigner.builder()
                    .portAssignment(DYNAMIC)
                    .serverFactory(factory)
                    .portSecurity(SECURE)
                    .tlsConfiguration(tlsConfig)
                    .build();

           var ports = assigner.assignDynamicPorts();
           assertThat(ports).hasSize(2);

            var adminConnector = assertIsExactType(first(factory.getAdminConnectors()), HttpsConnectorFactory.class);

            assertSecureConnectorProperties(adminConnector, tlsConfig);
        }

        private static void assertSecureConnectorProperties(HttpsConnectorFactory httpsConnectorFactory,
                                                            TlsContextConfiguration tlsConfig) {

            assertAll(
                    () -> assertThat(httpsConnectorFactory.getKeyStorePath()).isEqualTo(tlsConfig.getKeyStorePath()),
                    () -> assertThat(httpsConnectorFactory.getKeyStorePassword()).isEqualTo(tlsConfig.getKeyStorePassword()),
                    () -> assertThat(httpsConnectorFactory.getKeyStoreType()).isEqualTo(tlsConfig.getKeyStoreType()),
                    () -> assertThat(httpsConnectorFactory.getKeyStoreProvider()).isEqualTo(tlsConfig.getKeyStoreProvider()),
                    () -> assertThat(httpsConnectorFactory.getTrustStorePath()).isEqualTo(tlsConfig.getTrustStorePath()),
                    () -> assertThat(httpsConnectorFactory.getTrustStorePassword()).isEqualTo(tlsConfig.getTrustStorePassword()),
                    () -> assertThat(httpsConnectorFactory.getTrustStoreType()).isEqualTo(tlsConfig.getTrustStoreType()),
                    () -> assertThat(httpsConnectorFactory.getTrustStoreProvider()).isEqualTo(tlsConfig.getTrustStoreProvider()),
                    () -> assertThat(httpsConnectorFactory.getJceProvider()).isEqualTo(tlsConfig.getProvider()),
                    () -> assertThat(httpsConnectorFactory.getCertAlias()).isEqualTo(tlsConfig.getCertAlias()),
                    () -> assertThat(httpsConnectorFactory.getSupportedProtocols()).isEqualTo(tlsConfig.getSupportedProtocols()),
                    () -> assertThat(httpsConnectorFactory.getSupportedCipherSuites()).isEqualTo(tlsConfig.getSupportedCiphers()),
                    () -> assertThat(httpsConnectorFactory.isDisableSniHostCheck()).isEqualTo(tlsConfig.isDisableSniHostCheck())
            );
        }

        @Test
        void shouldSetupNonSecureDynamicPorts_WhenNonSecureSpecified() {
            var factory = new DefaultServerFactory();
            var minPortNumber = 9_000;
            var maxPortNumber = 9_100;
            var allowedRange = new AllowablePortRange(minPortNumber, maxPortNumber);

            var assigner = PortAssigner.builder()
                    .portAssignment(DYNAMIC)
                    .serverFactory(factory)
                    .portSecurity(NON_SECURE)
                    .allowablePortRange(allowedRange)
                    .build();

            var originalApplicationPort = firstApplicationPort(factory);
            var originalAdminPort = firstAdminPort(factory);

            var ports = assigner.assignDynamicPorts();

            assertThat(first(ports).getNumber())
                    .isNotEqualTo(originalApplicationPort)
                    .isBetween(minPortNumber, maxPortNumber);

            assertThat(second(ports).getNumber())
                    .isNotEqualTo(originalAdminPort)
                    .isBetween(minPortNumber, maxPortNumber);

            var applicationConnector = assertIsExactType(first(factory.getApplicationConnectors()), HttpConnectorFactory.class);
            assertThat(applicationConnector.getPort()).isBetween(minPortNumber, maxPortNumber);

            var adminConnector = assertIsExactType(first(factory.getAdminConnectors()), HttpConnectorFactory.class);
            assertThat(adminConnector.getPort()).isBetween(minPortNumber, maxPortNumber);
        }

        @Test
        void shouldModifyExistingConnectors_WhenNonSecureSpecified() {
            var factory = new DefaultServerFactory();
            var httpsAppConnector = new HttpsConnectorFactory();
            var httpsAdminConnector = new HttpsConnectorFactory();
            factory.setApplicationConnectors(List.of(httpsAppConnector));
            factory.setAdminConnectors(List.of(httpsAdminConnector));

            var minPortNumber = 15_000;
            var maxPortNumber = 16_000;
            var allowedRange = new AllowablePortRange(minPortNumber, maxPortNumber);

            var assigner = PortAssigner.builder()
                    .portAssignment(DYNAMIC)
                    .serverFactory(factory)
                    .portSecurity(NON_SECURE)
                    .allowablePortRange(allowedRange)
                    .build();

            var originalApplicationPort = firstApplicationPort(factory);
            var originalAdminPort = firstAdminPort(factory);

            var ports = assigner.assignDynamicPorts();

            assertThat(first(ports).getNumber())
                    .isNotEqualTo(originalApplicationPort)
                    .isBetween(minPortNumber, maxPortNumber);

            assertThat(first(ports).getSecure()).isEqualTo(Port.Security.SECURE);

            assertThat(second(ports).getNumber())
                    .isNotEqualTo(originalAdminPort)
                    .isBetween(minPortNumber, maxPortNumber);

            assertThat(second(ports).getSecure()).isEqualTo(Port.Security.SECURE);
        }

        @Test
        void shouldSetupNonSecureDynamicPorts_AsZero_WhenPortRangeExcluded() {
            var factory = new DefaultServerFactory();

            var assigner = PortAssigner.builder()
                    .portAssignment(DYNAMIC)
                    .serverFactory(factory)
                    .portSecurity(NON_SECURE)
                    .build();

            var ports = assigner.assignDynamicPorts();

            assertThat(first(ports).getNumber()).isZero();
            assertThat(second(ports).getNumber()).isZero();

            var applicationConnector = assertIsExactType(first(factory.getApplicationConnectors()), HttpConnectorFactory.class);
            assertThat(applicationConnector.getPort()).isZero();

            var adminConnector = assertIsExactType(first(factory.getAdminConnectors()), HttpConnectorFactory.class);
            assertThat(adminConnector.getPort()).isZero();
        }

        @Test
        void shouldThrowNoAvailablePortException_WhenNoPortsAvailable() {
            var factory = new DefaultServerFactory();
            var minPortNumber = 9_000;
            var maxPortNumber = 9_100;
            var allowedRange = new AllowablePortRange(minPortNumber, maxPortNumber);

            var freePortFinder = mock(FreePortFinder.class);
            when(freePortFinder.find(any()))
                    .thenThrow(new NoAvailablePortException("Did not find any open ports!"));

            var assigner = PortAssigner.builder()
                    .portAssignment(DYNAMIC)
                    .serverFactory(factory)
                    .portSecurity(NON_SECURE)
                    .allowablePortRange(allowedRange)
                    .freePortFinder(freePortFinder)
                    .build();

            assertThatThrownBy(assigner::assignDynamicPorts)
                    .isInstanceOf(NoAvailablePortException.class)
                    .hasMessage("Did not find any open ports!");
        }

        @Test
        void shouldAllowCustomFreePortFinder() {
            var factory = new DefaultServerFactory();

            var applicationPort = 42_000;
            var adminPort = 42_001;

            var assigner = PortAssigner.builder()
                    .portSecurity(NON_SECURE)
                    .serverFactory(factory)
                    .freePortFinder(ignoredPortRange -> new ServicePorts(applicationPort, adminPort))
                    .build();

            var ports = assigner.assignDynamicPorts();

            assertThat(first(ports).getNumber()).isEqualTo(applicationPort);
            assertThat(second(ports).getNumber()).isEqualTo(adminPort);

            var applicationConnector = assertIsExactType(first(factory.getApplicationConnectors()), HttpConnectorFactory.class);
            assertThat(applicationConnector.getPort()).isEqualTo(applicationPort);

            var adminConnector = assertIsExactType(first(factory.getAdminConnectors()), HttpConnectorFactory.class);
            assertThat(adminConnector.getPort()).isEqualTo(adminPort);
        }
    }

    private static int firstApplicationPort(DefaultServerFactory serverFactory) {
        var appConnectors = serverFactory.getApplicationConnectors();
        return getPort(first(appConnectors));
    }

    private static int firstAdminPort(DefaultServerFactory serverFactory) {
        var adminConnectors = serverFactory.getAdminConnectors();
        return getPort(first(adminConnectors));
    }

    private static int getPort(ConnectorFactory connectorFactory) {
        return ((HttpConnectorFactory) connectorFactory).getPort();
    }

    @Nested
    class PortAssignmentEnum {

        @ParameterizedTest
        @CsvSource(textBlock = """
            true, DYNAMIC
            false, STATIC
            """)
        void shouldCreateFromBooleanValues(boolean value, PortAssignment expectedPortAssignment) {
            assertThat(PortAssignment.fromBooleanDynamicWhenTrue(value)).isEqualTo(expectedPortAssignment);
        }
    }

    @Nested
    class PortSecurityEnum {

        @ParameterizedTest
        @CsvSource(textBlock = """
            true, SECURE
            false, NON_SECURE
            """)
        void shouldCreateFromBooleanValues(boolean value, PortSecurity expectedPortSecurity) {
            assertThat(PortSecurity.fromBooleanSecureWhenTrue(value)).isEqualTo(expectedPortSecurity);
        }

        @ParameterizedTest
        @CsvSource(textBlock = """
            SECURE, SECURE
            NOT_SECURE, NON_SECURE
            """)
        void shouldCreateFromSecurityEnumInPortClass(Port.Security security, PortSecurity expectedPortSecurity) {
            assertThat(PortSecurity.fromSecurity(security)).isEqualTo(expectedPortSecurity);
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Test
        void shouldRequireNonNullSecurityEnumValue() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> PortSecurity.fromSecurity(null))
                    .withMessage("security must not be null");
        }

        @ParameterizedTest
        @CsvSource(textBlock = """
            SECURE, SECURE
            NON_SECURE, NOT_SECURE
            """)
        void shouldConvertToSecurityEnumInPortClass(PortSecurity portSecurity, Port.Security expectedSecurity) {
            assertThat(portSecurity.toSecurity()).isEqualTo(expectedSecurity);
        }
    }
}
