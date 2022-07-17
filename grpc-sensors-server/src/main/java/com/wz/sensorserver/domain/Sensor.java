package com.wz.sensorserver.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

public class Sensor {
    public enum OnlineStatus {
        UNKNOWN(0), OFFLINE(1), ONLINE(2);
        @Getter
        final int value;

        OnlineStatus(int value) {
            this.value = value;
        }
    }

    @Getter
    @Setter
    private UUID id;
    @Getter
    private final String name;
    @Getter
    private final String location;
    @Getter
    private final List<String> tags;
    @Getter
    @Setter
    private volatile OnlineStatus onlineStatus;

    private final MeasurementHistory measurementHistory;

    @Builder
    public Sensor(UUID id, String name, String location, Collection<String> tags, int historyCapacity) {
        if (historyCapacity < 0)
            throw new IllegalArgumentException("History capacity must not be less than 0");
        this.id = id;
        this.name = name;
        this.location = location;
        this.tags = new ArrayList<>();
        if (tags != null)
            this.tags.addAll(tags);
        measurementHistory = new MeasurementHistoryEvictingQueueBased(historyCapacity);
        onlineStatus = OnlineStatus.OFFLINE;
    }

    public void putMeasurement(Measurement measurement) {
        measurementHistory.putMeasurement(measurement);
    }

    public List<Measurement> getMeasurements(int n) {
        return measurementHistory.getMeasurements(n);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Sensor sensor = (Sensor) o;
        return Objects.equals(id, sensor.id) && name.equals(sensor.name) && location.equals(sensor.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, location);
    }
}
