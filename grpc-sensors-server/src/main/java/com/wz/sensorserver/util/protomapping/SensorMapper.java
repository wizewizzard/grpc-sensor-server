package com.wz.sensorserver.util.protomapping;

import com.wz.sensors.proto.SensorInfoResponse;
import com.wz.sensors.proto.SensorRegistrationRequest;
import com.wz.sensorserver.domain.Sensor;

public class SensorMapper {
    public SensorInfoResponse mapDomainToResponse(Sensor sensor){
        return SensorInfoResponse
                .newBuilder()
                .setId(sensor.getId().toString())
                .setName(sensor.getName())
                .setLocation(sensor.getLocation())
                .setOnlineStatusValue(sensor.getOnlineStatus().getValue())
                .addAllTags(sensor.getTags())
                .build();
    }

    public Sensor mapRequestToDomain(SensorRegistrationRequest request){
        return Sensor
                .builder()
                .name(request.getName())
                .location(request.getLocation())
                .tags(request.getTagsList())
                .build();
    }
}
