package io.github.lothar1998.kuberesolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ResolverTargetTest {

        @ParameterizedTest
        @MethodSource("testCases")
        void parseURIParamsTest(String inputURL, ResolverTarget expectedParams) throws URISyntaxException {
                var uri = new URI(inputURL);

                var params = ResolverTarget.parse(uri);

                assertEquals(expectedParams, params);
        }

        @Test
        void parseInvalidURIParamsTest() throws URISyntaxException {
                var uri = new URI("");

                assertThrows(IllegalArgumentException.class, () -> ResolverTarget.parse(uri));
        }

        private static Stream<Arguments> testCases() {
                return Stream.of(
                                Arguments.of("kubernetes:///service-name:8080",
                                                new ResolverTarget(null, "service-name", "8080")),
                                Arguments.of("kubernetes:///service-name",
                                                new ResolverTarget(null, "service-name", null)),
                                Arguments.of("kubernetes:///service-name:portname",
                                                new ResolverTarget(null, "service-name", "portname")),
                                Arguments.of("kubernetes:///service-name.namespace:8080",
                                                new ResolverTarget("namespace", "service-name", "8080")),
                                Arguments.of("kubernetes:///service-name.namespace.svc.cluster_name",
                                                new ResolverTarget("namespace", "service-name", null)),
                                Arguments.of("kubernetes:///service-name.namespace.svc.cluster_name:8080",
                                                new ResolverTarget("namespace", "service-name", "8080")),
                                Arguments.of("kubernetes://namespace/service-name:8080",
                                                new ResolverTarget("namespace", "service-name", "8080")),
                                Arguments.of("kubernetes://service-name",
                                                new ResolverTarget(null, "service-name", null)),
                                Arguments.of("kubernetes://service-name:8080/",
                                                new ResolverTarget(null, "service-name", "8080")),
                                Arguments.of("kubernetes://service-name.namespace:8080/",
                                                new ResolverTarget("namespace", "service-name", "8080")),
                                Arguments.of("kubernetes://service-name.namespace.svc.cluster_name",
                                                new ResolverTarget("namespace", "service-name", null)),
                                Arguments.of("kubernetes://service-name.namespace.svc.cluster_name:8080",
                                                new ResolverTarget("namespace", "service-name", "8080")));
        }
}
