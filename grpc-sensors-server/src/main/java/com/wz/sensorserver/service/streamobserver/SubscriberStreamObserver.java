package com.wz.sensorserver.service.streamobserver;

import com.wz.sensors.proto.*;
import com.wz.sensorserver.domain.Sensor;
import com.wz.sensorserver.exception.SubscriptionException;
import com.wz.sensorserver.mq.MQConnectivity;
import com.wz.sensorserver.mq.MQSubscriptionManager;
import com.wz.sensorserver.mq.MQSubscriptionManagerImpl;
import com.wz.sensorserver.mq.message.MeasurementMessage;
import com.wz.sensorserver.mq.message.SensorOnlineStatusChanged;
import com.wz.sensorserver.repository.SensorRepository;
import com.wz.sensorserver.util.protomapping.MeasurementMapper;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Allows bidirectional streaming where user sends ids of sensor he needs to connect / disconnect. And the server
 * responses with a stream of measurements
 */
@Slf4j
public class SubscriberStreamObserver implements StreamObserver<SubscribeRequest> {
    private final SensorRepository sensorRepository;
    private final MQConnectivity mqConnectivity;
    private final StreamObserver<SubscriptionResponse> responseObserver;
    private final Lock responseObserverLock;
    //private final ClientSubscription<MQConsumer<? extends Serializable>> clientSubscription;
    private final MeasurementMapper mapper = new MeasurementMapper();
    private final MQSubscriptionManager<String, ? super Serializable> subscriptionManager;

    public SubscriberStreamObserver(MQConnectivity mqConnectivity,
                                    StreamObserver<SubscriptionResponse> responseObserver,
                                    SensorRepository sensorRepository
    ) {
        this.mqConnectivity = mqConnectivity;
        //this.clientSubscription = new ClientSubscription<>();
        this.sensorRepository = sensorRepository;
        this.responseObserver = responseObserver;
        this.responseObserverLock = new ReentrantLock();
        subscriptionManager = new MQSubscriptionManagerImpl<>(mqConnectivity);
    }

    @Override
    public void onNext(SubscribeRequest request) {
        SubscriptionResponse response;
        Optional<Sensor> sensorOptional = sensorRepository.getSensorById(UUID.fromString(request.getSensorId()));
        if (sensorOptional.isEmpty()) {
            response = buildActionResultMessage(ActionSuccessStatus.INVALID_REQUEST,
                    "Sensor you specified does not exist");
        } else {
            if (!request.getDisconnect()) { // it is a subscription request
                try {
                    subscriptionManager.trySubscribe(request.getSensorId(), (message) -> {
                        log.debug("Sending message to the client");
                        SubscriptionResponse subscriptionResponse;
                        if(message.getClass().equals(MeasurementMessage.class)){
                            subscriptionResponse = SubscriptionResponse
                                    .newBuilder()
                                    .setMeasurement(mapper.mapMeasurementMessageToResponse((MeasurementMessage) message))
                                    .build();
                        } else if (message.getClass().equals(SensorOnlineStatusChanged.class)) {
                            SensorOnlineStatusChanged statusChanged = (SensorOnlineStatusChanged) message;
                            subscriptionResponse = SubscriptionResponse
                                    .newBuilder()
                                    .setOnlineStatusChange(
                                            OnlineStatusChange
                                                    .newBuilder()
                                                    .setSensorId(statusChanged.getSensorId())
                                                    .setOnlineStatusValue(statusChanged.getSensorOnlineStatus().getValue())
                                                    .build()
                                    )
                                    .build();
                        }
                        else{
                            subscriptionResponse = SubscriptionResponse.newBuilder().getDefaultInstanceForType();
                        }
                        responseObserverLock.lock();
                        try {
                            responseObserver.onNext(subscriptionResponse);
                        } finally {
                            responseObserverLock.unlock();
                        }
                    });
                    response = buildActionResultMessage(ActionSuccessStatus.SUBSCRIPTION_SUCCESS,
                            "You are now subscribed on sensor: %s".formatted(request.getSensorId()));
                } catch (SubscriptionException subscriptionException) {
                    response = buildActionResultMessage(ActionSuccessStatus.SUBSCRIPTION_FAILURE,
                            "You are already subscribed: %s".formatted(request.getSensorId()));
                }
            } else {//unsubscribe from sensor
                try {
                    subscriptionManager.tryUnsubscribe(request.getSensorId());
                    response = buildActionResultMessage(ActionSuccessStatus.UNSUBSCRIPTION_SUCCESS,
                            "You have unsubscribed from sensor: %s".formatted(request.getSensorId()));
                } catch (SubscriptionException subscriptionException) {
                    log.info("Client is not subscribed to perform this operation");
                    response = buildActionResultMessage(ActionSuccessStatus.INVALID_REQUEST,
                            subscriptionException.getMessage());
                }
            }
        }
        responseObserverLock.lock();
        try {
            responseObserver.onNext(response);
        } finally {
            responseObserverLock.unlock();
        }
    }

    @Override
    public void onError(Throwable t) {
        log.debug("Error from client side received. Utilizing resources");
        subscriptionManager.clearSubscriptions();
        Status status = Status.UNKNOWN.withDescription("Error on client side detected");
        responseObserverLock.lock();
        try {
            responseObserver.onError(status.asRuntimeException());
        } finally {
            responseObserverLock.unlock();
        }
    }

    @Override
    public void onCompleted() {
        log.debug("Client wants to disconnect. Utilizing resources");
        subscriptionManager.clearSubscriptions();
        SubscriptionResponse response = buildActionResultMessage(ActionSuccessStatus.DISCONNECT_OK,
                "Goodbye");
        responseObserverLock.lock();
        try {
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } finally {
            responseObserverLock.unlock();
        }

    }

    private SubscriptionResponse buildActionResultMessage(ActionSuccessStatus status, String message) {
        return SubscriptionResponse.newBuilder().setActionResult(
                ActionResult.newBuilder()
                        .setActionStatus(status)
                        .setMessage(message)
                        .build()
        ).build();
    }
}
