package com.wz.sensorserver.repository;

import com.wz.sensorserver.domain.Sensor;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SensorRepositoryInMemory implements SensorRepository {

    private final List<Sensor> sensors;
    private final ReadWriteLock readWriteLock;

    public SensorRepositoryInMemory() {
        sensors = new ArrayList<>();
        readWriteLock = new ReentrantReadWriteLock();
    }

    @Override
    public void addSensor(Sensor sensor) {
        readWriteLock.writeLock().lock();
        try {
            sensors.add(sensor);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    @Override
    public boolean checkIfExists(Sensor sensor) {
        readWriteLock.readLock().lock();
        try {
            if (sensor.getId() == null)
                return sensors.stream()
                        .anyMatch(s -> s.getName().equals(sensor.getName()) && s.getLocation().equals(sensor.getLocation()));
            else
                return getSensorById(sensor.getId()).isPresent();
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public Optional<Sensor> getSensorById(UUID sensorId) {
        return sensors.stream()
                .filter(sensor -> sensor.getId().equals(sensorId))
                .findFirst();
    }

    @Override
    public Collection<Sensor> getSensors() {
        readWriteLock.readLock().lock();
        try {
            return Collections.unmodifiableCollection(sensors);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public Collection<Sensor> findSensorsByTags(Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return getSensors();
        }
        readWriteLock.readLock().lock();
        try {
            return sensors.stream()
                    .filter(sensor -> sensor.getTags().stream().anyMatch(tags::contains))
                    .toList();
        } finally {
            readWriteLock.readLock().unlock();
        }
    }
}
