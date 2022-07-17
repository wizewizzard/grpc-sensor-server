package com.wz.sensorserver.repository;


import com.wz.sensorserver.domain.Sensor;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

public interface SensorRepository {
    /**
     * Adds new sensor to pool
     *
     * @param sensor
     */
    void addSensor(Sensor sensor);

    /**
     * Check if sensor exists either by id if it's not null or by name and location
     *
     * @param sensor
     * @return
     */
    boolean checkIfExists(Sensor sensor);

    /**
     * Retrieves sensor by id if it exists
     *
     * @param sensorId
     * @return Optional sensor
     */
    Optional<Sensor> getSensorById(UUID sensorId);

    /**
     * @return collection of all sensors
     */
    Collection<Sensor> getSensors();

    Collection<Sensor> findSensorsByTags(Collection<String> tags);
}
