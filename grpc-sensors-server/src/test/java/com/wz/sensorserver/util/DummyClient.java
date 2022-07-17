package com.wz.sensorserver.util;

import com.wz.sensors.proto.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.Executor;

@Slf4j
public class DummyClient {
    private final String login;
    private final String email;
    private final String password;
    private String token;
    private final ManagedChannel channel;
    private final SensorClientServiceGrpc.SensorClientServiceStub asyncStub;
    private final SensorClientServiceGrpc.SensorClientServiceBlockingStub blockingStub;
    @Setter
    private Runnable onRegistration;
    @Setter
    private Runnable onSubscription;
    @Setter
    private Runnable onSubscriptionFailure;
    @Setter
    private Runnable onMeasurementReceived;
    @Setter
    private Runnable onComplete;
    @Setter
    private Runnable onError;

    @Getter
    @Setter
    private StreamObserver<SubscribeRequest> requestStreamObserver;
    @Getter
    @Setter
    private StreamObserver<SubscriptionResponse> responseStreamObserver;

    public DummyClient(String login, String email, String password, int port) {
        this.login = login;
        this.email = email;
        this.password = password;
        this.channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .directExecutor()
                .build();
        asyncStub = SensorClientServiceGrpc.newStub(channel);
        blockingStub = SensorClientServiceGrpc.newBlockingStub(channel);
    }

    public void register() {
        ClientRegistrationRequest request = ClientRegistrationRequest
                .newBuilder()
                .setLogin(login)
                .setPassword(password)
                .setEmail(email)
                .build();
        TokenResponse tokenResponse = blockingStub.registerClient(request);
        if (onRegistration != null)
            onRegistration.run();
        this.token = tokenResponse.getToken();
    }

    public void createBidirectionalStream() {
        responseStreamObserver = responseStreamObserver == null ? defaultResponseStreamObserver() : responseStreamObserver;
        this.requestStreamObserver = asyncStub
                .withCallCredentials(createCallCredentialsWithToken())
                .subscribeOnSensor(responseStreamObserver);
    }

    public void sendSubscribeRequest(SubscribeRequest request) {
        log.trace("Client >>> Subscribing on: {}", request.getSensorId());
        Objects.requireNonNull(requestStreamObserver);
        Objects.requireNonNull(request);
        requestStreamObserver.onNext(request);
    }

    public void disconnect() {
        requestStreamObserver.onCompleted();
    }

    public void cleanUp() {
        channel.shutdown();
    }

    public CallCredentials createCallCredentialsWithToken() {
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

    public StreamObserver<SubscriptionResponse> defaultResponseStreamObserver() {
        return new StreamObserver<>() {
            @Override
            public void onNext(SubscriptionResponse response) {
                switch (response.getResponseCase()) {
                    case MEASUREMENT:
                        if (onMeasurementReceived != null) onMeasurementReceived.run();
                        //log.info("Client >>> Got something! {}", response.getMeasurement().getValue());

                        break;
                    case ONLINESTATUSCHANGE:
                        log.info("Client >>> Sensors online status changed: {}",
                                SensorOnlineStatus.forNumber(response.getOnlineStatusChange().getOnlineStatusValue()));
                        break;
                    case ACTIONRESULT:
                        if (response.getActionResult().getActionStatus().equals(ActionSuccessStatus.SUBSCRIPTION_SUCCESS)) {
                            if (onSubscription != null) onSubscription.run();
                        } else if (response.getActionResult().getActionStatus().equals(ActionSuccessStatus.SUBSCRIPTION_FAILURE)) {
                            if (onSubscriptionFailure != null) onSubscriptionFailure.run();
                        }
                        //log.info("Client >>> Action result is: {}", response.getActionResult().getMessage());
                        break;
                }
            }

            @Override
            public void onError(Throwable t) {
                if (onError != null) onError.run();
                log.error("Client >>> Error! ", t);
            }

            @Override
            public void onCompleted() {
                if (onComplete != null) onComplete.run();
                log.info("Client >>> Observing is over!");
            }
        };
    }
}
