package com.wz.sensorserver.service;

import com.google.protobuf.Empty;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.wz.sensors.proto.*;
import com.wz.sensorserver.constant.Constants;
import com.wz.sensorserver.domain.Sensor;
import com.wz.sensorserver.mq.MQConnectivity;
import com.wz.sensorserver.mq.MQPublisher;
import com.wz.sensorserver.mq.Publisher;
import com.wz.sensorserver.mq.message.MeasurementMessage;
import com.wz.sensorserver.mq.message.SensorOnlineStatusChanged;
import com.wz.sensorserver.repository.SensorRepository;
import com.wz.sensorserver.service.streamobserver.NoopStreamObserver;
import com.wz.sensorserver.service.streamobserver.SensorStreamObserver;
import com.wz.sensorserver.util.protomapping.MeasurementMapper;
import com.wz.sensorserver.util.protomapping.SensorMapper;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class SensorService extends SensorServiceGrpc.SensorServiceImplBase {

    private final AuthenticationService authenticationService;
    private final SensorRepository sensorRepository;
    private final MQConnectivity mqConnectivity;

    public SensorService(MQConnectivity mqConnectivity,
                         AuthenticationService authenticationService,
                         SensorRepository sensorRepository
    ) {
        this.mqConnectivity = mqConnectivity;
        this.authenticationService = authenticationService;
        this.sensorRepository = sensorRepository;
    }

    @Override
    public void registerSensor(SensorRegistrationRequest request, StreamObserver<SensorRegistrationResponse> responseObserver) {
        SensorMapper mapper = new SensorMapper();
        final Sensor sensorToRegister = mapper.mapRequestToDomain(request);

        if (!sensorRepository.checkIfExists(sensorToRegister)) {
            UUID sensorId = UUID.randomUUID();
            String jwtToken = authenticationService.generateToken(Map.of("sensorId", sensorId.toString()));
            SensorRegistrationResponse response = SensorRegistrationResponse
                    .newBuilder()
                    .setToken(jwtToken)
                    .setId(sensorId.toString())
                    .build();
            sensorToRegister.setId(sensorId);
            sensorRepository.addSensor(sensorToRegister);
            log.info("Sensor was registered. ID: {}", sensorId);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } else {
            log.info("Sensor was not registered. Already exists.");
            Status status = Status.ALREADY_EXISTS
                    .withDescription("Sensor with given name and location already exists");
            responseObserver.onError(status.asRuntimeException());
        }
    }

    @Override
    public StreamObserver<MeasurementRequest> sendMeasurements(StreamObserver<Empty> responseObserver) {
        log.trace("Sensor trying to connected to start streaming");
        UUID sensorId = UUID.fromString(Constants.SENSOR_ID_CONTEXT_KEY.get());
        Optional<Sensor> sensorOptional = sensorRepository.getSensorById(sensorId);
        if (sensorOptional.isPresent()) {
            Sensor sensor = sensorOptional.get();
            if (sensor.getOnlineStatus().equals(Sensor.OnlineStatus.OFFLINE)) {
                try {
                    sensor.setOnlineStatus(Sensor.OnlineStatus.ONLINE);
                    Channel channel = mqConnectivity.newChannel();
                    channel.exchangeDeclare(sensorId.toString(), BuiltinExchangeType.FANOUT, false, false, null);
                    Publisher<? super Serializable> publisher = new MQPublisher<>(channel, sensorId.toString(), "");
                    publisher.publishMessage(new SensorOnlineStatusChanged(sensorId.toString(), Sensor.OnlineStatus.ONLINE));
                    return new SensorStreamObserver(responseObserver, publisher, sensor);
                } catch (IOException exception) {
                    log.error("Error when managing the exchange for sensor {}", sensorId, exception);
                    Status status = Status.INTERNAL
                            .withDescription("Something went wrong on the server.");
                    responseObserver.onError(status.asRuntimeException());
                    return new NoopStreamObserver<>();
                }
            }
        }
        log.info("SensorId was not accepted because the sensor with the given id does not exist or is already connected");
        Status status = Status.NOT_FOUND
                .withDescription("Token your specified has no sensor associated with it. Or this sensor is already connected");
        responseObserver.onError(status.asRuntimeException());
        return new NoopStreamObserver<>();
    }

    @Override
    public void getHistoryForSensor(SensorHistoryRequest request, StreamObserver<SensorHistoryResponse> responseObserver) {
        final MeasurementMapper measurementMapper = new MeasurementMapper();
        String sensorId = request.getSensorId();
        int depth = request.getDepth();
        Optional<Sensor> sensorOptional = sensorRepository.getSensorById(UUID.fromString(sensorId));
        if (sensorOptional.isPresent()) {
            Sensor sensor = sensorOptional.get();
            SensorHistoryResponse sensorHistory = SensorHistoryResponse.newBuilder()
                    .addAllMeasurements(sensor.getMeasurements(depth)
                            .stream()
                            .map(measurementMapper::mapDomainToResponse)
                            .collect(Collectors.toList()))
                    .setSensorStatusValue(sensor.getOnlineStatus().getValue())
                    .build();
            responseObserver.onNext(sensorHistory);
            responseObserver.onCompleted();
        } else {
            Status status = Status.NOT_FOUND
                    .withDescription("Sensor with given id does not exist");
            responseObserver.onError(status.asRuntimeException());
        }
    }

    @Override
    public void getSensors(SearchTagsRequest request, StreamObserver<SensorInfoResponse> responseObserver) {
        SensorMapper mapper = new SensorMapper();
            sensorRepository.findSensorsByTags(request.getTagsList())
                    .stream()
                    .map(mapper::mapDomainToResponse)
                    .forEach(responseObserver::onNext);
        responseObserver.onCompleted();
    }

}
