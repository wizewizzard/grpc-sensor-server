package com.wz.sensorserver.mq.message;

import com.wz.sensorserver.domain.Measurement;
import com.wz.sensorserver.domain.Sensor;
import lombok.Data;

import java.io.Serializable;

/**
 * A message type that goes in and out of message queue
 */
@Data
public class MeasurementMessage implements Serializable {
    private final Measurement measurement;
    private final String sensorId;

}
