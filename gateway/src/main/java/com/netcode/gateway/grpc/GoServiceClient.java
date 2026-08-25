package com.netcode.gateway.grpc;

import com.netcode.gateway.proto.ClientMessage;
import com.netcode.gateway.proto.GameServiceGrpc;
import com.netcode.gateway.proto.ServerMessage;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Spring-managed gRPC client component connecting the Gateway to the backend Go game service.
 *
 * <p>Manages the lifecycle of the underlying {@link ManagedChannel} and provides access
 * to the asynchronous {@link GameServiceGrpc.GameServiceStub} for establishing bidirectional
 * game streams.</p>
 */
@Component
public class GoServiceClient {

    private static final Logger log = LoggerFactory.getLogger(GoServiceClient.class);

    private final String host;
    private final int port;

    private ManagedChannel channel;
    private GameServiceGrpc.GameServiceStub stub;

    /**
     * Constructs the gRPC service client with host and port injected from application configuration.
     *
     * @param host Target host of the Go game service (defaults to localhost).
     * @param port Target gRPC port of the Go game service (defaults to 9090).
     */
    public GoServiceClient(
            @Value("${go-service.host:localhost}") String host,
            @Value("${go-service.port:9090}") int port
    ) {
        this.host = host;
        this.port = port;
    }

    /**
     * Initializes the gRPC channel and async stub upon bean creation.
     * The connection is lazy and will not fail startup even if the Go service is not yet running.
     */
    @PostConstruct
    public void init() {
        log.info("[grpc] initializing gRPC connection to {}:{}", host, port);
        io.grpc.ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder.forAddress(host, port);
        
        if (host.contains("onrender.com")) {
            log.info("[grpc] enabling TLS transport security for public host: {}", host);
            channelBuilder.useTransportSecurity();
        } else {
            log.info("[grpc] using plaintext connection for local/internal host: {}", host);
            channelBuilder.usePlaintext();
        }
        
        this.channel = channelBuilder.build();
        this.stub = GameServiceGrpc.newStub(channel);
        log.info("[grpc] channel created -> {}:{}", host, port);
    }

    /**
     * Establishes a bidirectional streaming session with the Go game service.
     *
     * @param responseObserver Observer receiving state snapshots and server notifications.
     * @return StreamObserver used to stream client inputs and join/leave envelopes to Go.
     */
    public StreamObserver<ClientMessage> play(StreamObserver<ServerMessage> responseObserver) {
        if (stub == null) {
            throw new IllegalStateException("GoServiceClient gRPC stub has not been initialized.");
        }
        return stub.play(responseObserver);
    }

    /**
     * Gracefully shuts down the gRPC channel upon Spring application termination.
     */
    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            log.info("[grpc] shutting down gRPC channel to {}:{}", host, port);
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                log.info("[grpc] gRPC channel shut down successfully");
            } catch (InterruptedException e) {
                log.warn("[grpc] gRPC channel shutdown interrupted, forcing immediate termination");
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Returns the active gRPC {@link ManagedChannel}.
     *
     * @return The managed channel instance.
     */
    public ManagedChannel getChannel() {
        return channel;
    }

    /**
     * Returns the asynchronous {@link GameServiceGrpc.GameServiceStub}.
     *
     * @return The game service async stub.
     */
    public GameServiceGrpc.GameServiceStub getStub() {
        return stub;
    }

    /**
     * Returns configured host.
     *
     * @return Target host string.
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns configured port.
     *
     * @return Target port number.
     */
    public int getPort() {
        return port;
    }
}
