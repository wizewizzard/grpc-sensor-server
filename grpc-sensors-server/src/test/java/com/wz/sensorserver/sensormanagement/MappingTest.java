package com.wz.sensorserver.sensormanagement;


import com.google.protobuf.Timestamp;
import com.wz.sensors.proto.*;
import com.wz.sensorserver.domain.Client;
import com.wz.sensorserver.domain.Measurement;
import com.wz.sensorserver.domain.Sensor;
import com.wz.sensorserver.repository.ClientRepositoryInMemory;
import com.wz.sensorserver.util.protomapping.MeasurementMapper;
import com.wz.sensorserver.util.protomapping.SensorMapper;
import com.wz.sensorserver.mq.message.MeasurementMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.*;

class MappingTest {

    @Test
    public void testMeasurementRequestToMeasurementConversion(){
        Instant now = Instant.now();
        MeasurementMapper mapper = new MeasurementMapper();

        MeasurementRequest measurementProto = MeasurementRequest.newBuilder()
                .setMadeAt(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()).build())
                .setValue(Math.random())
                .build();
        Measurement measurementDomain = mapper.mapRequestToDomain(measurementProto);

        assertThat(measurementDomain).isNotNull();
        assertThat(measurementDomain.getValue()).isEqualTo(measurementProto.getValue());
        assertThat(measurementDomain.getMadeAt()).isEqualTo(Instant.ofEpochSecond(measurementProto.getMadeAt().getSeconds(), measurementProto.getMadeAt().getNanos()));
    }

    @Test
    public void testMeasurementToMeasurementResponseConversion(){
        MeasurementMessage measurementDomain = new MeasurementMessage(new Measurement(Math.random(), Instant.now()), UUID.randomUUID().toString());
        MeasurementMapper mapper = new MeasurementMapper();

        MeasurementResponse protoMeasurement = mapper.mapMeasurementMessageToResponse(measurementDomain);

        assertThat(protoMeasurement).isNotNull();
        assertThat(protoMeasurement.getValue()).isEqualTo(measurementDomain.getMeasurement().getValue());
        assertThat(protoMeasurement.getSensorId()).isEqualTo(measurementDomain.getSensorId());
        assertThat(protoMeasurement.getMadeAt())
                .isEqualTo(Timestamp.newBuilder()
                        .setSeconds(measurementDomain.getMeasurement().getMadeAt().getEpochSecond())
                        .setNanos(measurementDomain.getMeasurement().getMadeAt().getNano())
                        .build());
    }

    @Test
    public void testSensorRegRequestToSensorDomainConversion(){
        SensorMapper underTest = new SensorMapper();
        Collection<String> tags = List.of("cave", "gas-level");
        SensorRegistrationRequest sensorRegisterRequest = SensorRegistrationRequest.newBuilder()
                .setName("cave-gas-sensor")
                .setLocation("Diamond cave in Siberia")
                .addAllTags(tags)
                .build();

        Sensor sensorDomain = underTest.mapRequestToDomain(sensorRegisterRequest);

        assertThat(sensorDomain).isNotNull();
        assertThat(sensorDomain.getName()).isEqualTo(sensorRegisterRequest.getName());
        assertThat(sensorDomain.getLocation()).isEqualTo(sensorRegisterRequest.getLocation());
        assertThat(sensorDomain.getTags()).containsAll(sensorRegisterRequest.getTagsList());

    }

    @Test
    public void testSensorDomainToSensorInfoResponseConversion(){
        SensorMapper underTest = new SensorMapper();
        Collection<String> tags = List.of("cave", "gas-level");
        Sensor sensorDomain = Sensor
                .builder()
                .id(UUID.randomUUID())
                .name("cave-gas-sensor")
                .location("Diamond cave in Siberia")
                .tags(tags)
                .build();
        sensorDomain.setOnlineStatus(Sensor.OnlineStatus.OFFLINE);

        SensorInfoResponse sensorInfoResponse = underTest.mapDomainToResponse(sensorDomain);

        assertThat(sensorInfoResponse).isNotNull();
        assertThat(sensorInfoResponse.getId()).isEqualTo(sensorDomain.getId().toString());
        assertThat(sensorInfoResponse.getName()).isEqualTo(sensorDomain.getName());
        assertThat(sensorInfoResponse.getLocation()).isEqualTo(sensorDomain.getLocation());
        assertThat(sensorInfoResponse.getTagsList()).containsAll(sensorDomain.getTags());
        assertThat(sensorInfoResponse.getOnlineStatus()).isEqualTo(SensorOnlineStatus.SENSOR_OFFLINE);
    }
}