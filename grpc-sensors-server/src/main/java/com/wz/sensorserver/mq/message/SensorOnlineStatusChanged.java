package com.wz.sensorserver.mq.message;

import com.wz.sensorserver.domain.Sensor;
import lombok.Data;

import java.io.Serializable;

@Data
public class SensorOnlineStatusChanged implements Serializable {
    private final String sensorId;
    private final Sensor.OnlineStatus sensorOnlineStatus;
}
