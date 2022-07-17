package com.wz.sensorserver.service.streamobserver;

import com.google.protobuf.Empty;
import com.wz.sensors.proto.MeasurementRequest;
import com.wz.sensorserver.domain.Measurement;
import com.wz.sensorserver.domain.Sensor;
import com.wz.sensorserver.mq.Publisher;
import com.wz.sensorserver.mq.message.MeasurementMessage;
import com.wz.sensorserver.mq.message.SensorOnlineStatusChanged;
import com.wz.sensorserver.util.protomapping.MeasurementMapper;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Sensor input measurements stream observer
 */
@Slf4j
public class SensorStreamObserver implements StreamObserver<MeasurementRequest> {
    private final StreamObserver<Empty> responseObserver;
    private final Lock responseObserverLock;
    private final Publisher<? super Serializable> publisher;
    private final MeasurementMapper measurementMapper;
    private final Sensor sensor;

    public SensorStreamObserver(StreamObserver<Empty> responseObserver,
                                Publisher<? super Serializable> publisher,
                                Sensor sensor) {
        Objects.requireNonNull(publisher);
        Objects.requireNonNull(responseObserver);
        Objects.requireNonNull(sensor);
        this.responseObserver = responseObserver;
        responseObserverLock = new ReentrantLock();
        this.publisher = publisher;
        this.measurementMapper = new MeasurementMapper();
        this.sensor = sensor;
    }

    @Override
    public void onNext(MeasurementRequest publishedMeasurement) {
        log.trace("Sensor published measurement");
        Measurement measurement = measurementMapper.mapRequestToDomain(publishedMeasurement);
        sensor.putMeasurement(measurement);
        MeasurementMessage message = new MeasurementMessage(
                measurementMapper.mapRequestToDomain(publishedMeasurement),
                sensor.getId().toString());
        publisher.publishMessage(message);
    }

    @Override
    public void onError(Throwable t) {
        log.error("Sensor sent an error");
        sensor.setOnlineStatus(Sensor.OnlineStatus.OFFLINE);
        publisher.publishMessage(new SensorOnlineStatusChanged(sensor.getId().toString(), Sensor.OnlineStatus.UNKNOWN));
        Status status = Status.UNKNOWN
                .withDescription("Sensor sent an error");
        publisher.cleanUp();
        responseObserverLock.lock();
        try {
            responseObserver.onError(status.asRuntimeException());
        } finally {
            responseObserverLock.unlock();
        }

    }

    @Override
    public void onCompleted() {
        log.trace("Sensor data transmission is over");
        sensor.setOnlineStatus(Sensor.OnlineStatus.OFFLINE);
        publisher.publishMessage(new SensorOnlineStatusChanged(sensor.getId().toString(), Sensor.OnlineStatus.OFFLINE));
        publisher.cleanUp();
        responseObserverLock.lock();
        try {
            responseObserver.onNext(Empty.newBuilder().getDefaultInstanceForType());
            responseObserver.onCompleted();
        } finally {
            responseObserverLock.unlock();
        }
    }
}
