package io.github.lothar1998.kuberesolver.integration.app;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lothar1998.kuberesolver.KubernetesNameResolverProvider;
import io.github.lothar1998.kuberesolver.integration.app.ip.IPServiceGrpc;
import io.github.lothar1998.kuberesolver.integration.app.ip.IpService;
import io.grpc.ManagedChannelBuilder;
import io.grpc.NameResolverRegistry;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableChannel;
import reactor.netty.http.server.HttpServer;

@Slf4j
public class Client {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        NameResolverRegistry.getDefaultRegistry().register(new KubernetesNameResolverProvider());
    }

    public static void main(String[] args) throws ParseException, JsonProcessingException {
        var config = parseArgs(args);
        log.info("starting client with configuration: target={}, port={}", config.target(), config.port());

        Map<String, Integer> stats = new ConcurrentHashMap<>();

        TypeReference<Map<String, ?>> typeRef = new TypeReference<>() {};
        Map<String, ?> serviceConfig = OBJECT_MAPPER.readValue("""
                {
                    "loadBalancingConfig": [ {
                        "round_robin": {}
                    } ]
                }
                """, typeRef);

        var channel = ManagedChannelBuilder
                .forTarget(config.target())
                .defaultServiceConfig(serviceConfig)
                .usePlaintext()
                .build();
        var server = runHTTPServer(config.port(), stats);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            channel.shutdownNow();
            server.disposeNow();
            log.info("client closed");
        }));

        log.info("starting discovering IP address");
        var stub = IPServiceGrpc.newBlockingStub(channel);

        try {
            while (true) {
                var response = stub.whatIsYourIP(IpService.IPRequest.newBuilder().build());
                log.info("discovered addresses {}", response.getIpAddressesList());
                response.getIpAddressesList().forEach(ip -> stats.merge(ip, 1, Integer::sum));

                try {
                    Thread.sleep(TimeUnit.MILLISECONDS.toMillis(100));
                } catch (InterruptedException ignored) {
                }
            }
        } catch (Exception e) {
            log.error("encountered error when discovering the IPs", e);
        }
    }

    private static DisposableChannel runHTTPServer(int port, Map<String, Integer> stats) {
        log.info("starting HTTP server on port {}", port);
        return HttpServer.create()
                .port(port)
                .route(routes -> {
                    routes.get("/ip", (req, res) -> {
                        try {
                            var json = OBJECT_MAPPER.writeValueAsString(stats);
                            log.info("handling request {}", json);
                            return res.status(HttpResponseStatus.OK).sendString(Mono.just(json));
                        } catch (JsonProcessingException e) {
                            return res.status(HttpResponseStatus.INTERNAL_SERVER_ERROR);
                        }
                    });
                    routes.delete("/ip", (req, res) -> {
                        stats.clear();
                        return res.status(HttpResponseStatus.OK);
                    });
                })
                .bindNow()
                .onDispose(() -> log.info("HTTP server closed"));
    }


    private static Config parseArgs(String[] args) throws ParseException {
        var options = new Options();
        var targetOption = Option.builder()
                .longOpt("target")
                .hasArg()
                .desc("gRPC target")
                .required()
                .build();
        options.addOption(targetOption);

        var portOption = Option.builder()
                .longOpt("port")
                .hasArg()
                .desc("HTTP server port")
                .required()
                .build();
        options.addOption(portOption);

        var parser = new DefaultParser();

        try {
            var cmd = parser.parse(options, args);
            return new Config(cmd.getOptionValue("target"), Integer.parseInt(cmd.getOptionValue("port")));
        } catch (ParseException e) {
            log.error("cannot parse cli flags");
            new HelpFormatter().printHelp("kuberesolver-test-client", options);
            throw e;
        }
    }

    private record Config(String target, int port) {
    }
}
