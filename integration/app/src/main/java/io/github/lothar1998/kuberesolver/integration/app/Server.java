package io.github.lothar1998.kuberesolver.integration.app;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import io.github.lothar1998.kuberesolver.integration.app.ip.IPServiceGrpc;
import io.github.lothar1998.kuberesolver.integration.app.ip.IpService;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

@Slf4j
public class Server {
    public static void main(String[] args) throws ParseException, InterruptedException, IOException {
        var config = parseConfig(args, System.getenv());
        log.info("starting server with configuration: port={}, ip_addr={}", config.port(), config.ipAddr());

        var server = Grpc.newServerBuilderForPort(config.port(), InsecureServerCredentials.create())
                .addService(new IPService(config.ipAddr()))
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdown();
            try {
                server.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            server.shutdownNow();
        }));

        server.start();
        server.awaitTermination();
    }

    private static Config parseConfig(String[] args, Map<String, String> envVariables) throws ParseException {
        var options = new Options();
        var portOption = Option.builder()
                .longOpt("port")
                .hasArg()
                .desc("gRPC port")
                .required()
                .build();

        portOption.setRequired(true);
        options.addOption(portOption);

        var parser = new DefaultParser();

        try {
            var cmd = parser.parse(options, args);
            return new Config(envVariables.get("IP_ADDR"), Integer.parseInt(cmd.getOptionValue("port")));
        } catch (ParseException e) {
            log.error("cannot parse cli flags");
            new HelpFormatter().printHelp("kuberesolver-test-server", options);
            throw e;
        }
    }

    private record Config(String ipAddr, int port) {
    }

    @RequiredArgsConstructor
    private static class IPService extends IPServiceGrpc.IPServiceImplBase {
        private final String ipAddr;

        @Override
        public void whatIsYourIP(IpService.IPRequest request, StreamObserver<IpService.IPResponse> responseObserver) {
            log.info("handling request for IP addresses");
            responseObserver.onNext(IpService.IPResponse.newBuilder().addIpAddresses(ipAddr).build());
            responseObserver.onCompleted();
        }
    }
}
