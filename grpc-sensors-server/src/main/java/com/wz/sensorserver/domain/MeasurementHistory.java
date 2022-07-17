package com.wz.sensorserver.domain;

import java.util.List;

public interface MeasurementHistory {
    /**
     * Saves measurement to the history
     *
     * @param measurement
     */
    void putMeasurement(Measurement measurement);

    /**
     * Returns given number n of measurements, or <= n, if there are not enough measurements yet
     *
     * @param n - history depth
     * @return measurements ordered by time they were made at desc
     */
    List<Measurement> getMeasurements(int n);
}
