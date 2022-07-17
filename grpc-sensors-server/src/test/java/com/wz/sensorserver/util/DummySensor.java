package com.wz.sensorserver.util;

import com.google.protobuf.Empty;
import com.wz.sensors.proto.MeasurementRequest;
import com.wz.sensors.proto.SensorRegistrationRequest;
import com.wz.sensors.proto.SensorServiceGrpc;
import com.wz.sensorserver.domain.Sensor;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.Executor;

@Slf4j
public class DummySensor {
    @Getter
    private String token;
    @Setter
    @Getter
    private String id;
    private final Sensor sensor;
    private final ManagedChannel channel;
    @Getter
    @Setter
    private StreamObserver<MeasurementRequest> requestObserver;
    @Getter
    @Setter
    private StreamObserver<Empty> responseObserver;
    private final SensorServiceGrpc.SensorServiceBlockingStub blockingStub;

    @Setter
    private Runnable onRegistration;
    @Setter
    private Runnable onComplete;
    @Setter
    private Runnable onError;

    public DummySensor(Sensor sensor, int port) {
        this.sensor = sensor;
        this.channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .directExecutor()
                .build();
        blockingStub = SensorServiceGrpc.newBlockingStub(channel);
    }

    public void registerSensor() {
        SensorRegistrationRequest sensorRegisterRequest = SensorRegistrationRequest
                .newBuilder()
                .setName(sensor.getName())
                .setLocation(sensor.getLocation())
                .addAllTags(sensor.getTags())
                .build();
        this.token = blockingStub.registerSensor(sensorRegisterRequest).getToken();
        if (onRegistration != null)
            onRegistration.run();
    }

    public void publishMeasurement(MeasurementRequest request) {
        Objects.requireNonNull(requestObserver);
        Objects.requireNonNull(request);
        requestObserver.onNext(request);
    }

    public void startStreaming() {
        responseObserver = responseObserver == null ? defaultResponseStreamObserver() : responseObserver;
        requestObserver = SensorServiceGrpc.newStub(channel)
                .withCallCredentials(createCallCredentialsWithToken())
                .sendMeasurements(responseObserver);
    }

    public void completeStreaming() {
        requestObserver.onCompleted();
    }

    public void cleanUp() {
        channel.shutdown();
    }

    private CallCredentials createCallCredentialsWithToken() {
        return new CallCredentials() {
            @Override
            public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor, MetadataApplier applier) {
                appExecutor.execute(() -> {
                    try {
                        Metadata headers = new Metadata();
                        headers.put(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER),
                                token);
                        applier.apply(headers);
                    } catch (Throwable e) {
                        applier.fail(Status.UNAUTHENTICATED.withCause(e));
                    }
                });
            }

            @Override
            public void thisUsesUnstableApi() {
            }
        };
    }

    private StreamObserver<Empty> defaultResponseStreamObserver() {
        return new StreamObserver<>() {
            @Override
            public void onNext(Empty response) {

            }

            @Override
            public void onError(Throwable t) {
                log.error("Sensor >>> Error when publishing measurements! ", t);
                if (onError != null) onError.run();
            }

            @Override
            public void onCompleted() {
                log.debug("Sensor >>> Publishing measurements completed!");
                if (onComplete != null) onComplete.run();
            }
        };
    }
}
