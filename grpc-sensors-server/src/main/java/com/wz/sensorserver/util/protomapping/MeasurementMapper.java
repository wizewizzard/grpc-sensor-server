package com.wz.sensorserver.util.protomapping;

import com.google.protobuf.Timestamp;
import com.wz.sensors.proto.MeasurementRequest;
import com.wz.sensors.proto.MeasurementResponse;
import com.wz.sensors.proto.SensorOnlineStatus;
import com.wz.sensorserver.domain.Measurement;
import com.wz.sensorserver.domain.Sensor;
import com.wz.sensorserver.mq.message.MeasurementMessage;

import java.time.Instant;

public class MeasurementMapper {
    /**
     * Maps measurement domain object to the response with same fields
     * @param measurement
     * @return
     */
    public MeasurementResponse mapDomainToResponse(Measurement measurement){
        return MeasurementResponse
                .newBuilder()
                .setValue(measurement.getValue())
                .setMadeAt(Timestamp
                        .newBuilder()
                        .setSeconds(measurement.getMadeAt().getEpochSecond())
                        .setNanos(measurement.getMadeAt().getNano())
                        .build())
                .build();
    }

    /**
     * Maps the given measurementMessage that contains the sensor online status aw well to the response
     * @param measurementMessage
     * @return
     */
    public MeasurementResponse mapMeasurementMessageToResponse(MeasurementMessage measurementMessage){
        return MeasurementResponse
                .newBuilder()
                .setSensorId(measurementMessage.getSensorId())
                .setValue(measurementMessage.getMeasurement().getValue())
                .setMadeAt(Timestamp
                        .newBuilder()
                        .setSeconds(measurementMessage.getMeasurement().getMadeAt().getEpochSecond())
                        .setNanos(measurementMessage.getMeasurement().getMadeAt().getNano())
                        .build())
                .build();
    }

    /**
     * Maps the received measurement to the domain measurement object
     * @param request
     * @return
     */
    public Measurement mapRequestToDomain(MeasurementRequest request){
        return new Measurement(request.getValue(),
                Instant.ofEpochSecond(request.getMadeAt().getSeconds(), request.getMadeAt().getNanos()));
    }
}
