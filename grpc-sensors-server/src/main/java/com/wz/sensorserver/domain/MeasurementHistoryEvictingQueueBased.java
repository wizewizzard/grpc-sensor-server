package com.wz.sensorserver.domain;

import com.google.common.collect.EvictingQueue;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class MeasurementHistoryEvictingQueueBased implements MeasurementHistory {
    private static final int DEFAULT_HISTORY_SIZE = 50;
    private final EvictingQueue<Measurement> measurementHistory;

    private final ReadWriteLock historyLock;

    public MeasurementHistoryEvictingQueueBased() {
        this(DEFAULT_HISTORY_SIZE);
    }

    public MeasurementHistoryEvictingQueueBased(int historyCapacity) {
        if(historyCapacity == 0)
            historyCapacity = DEFAULT_HISTORY_SIZE;
        measurementHistory = EvictingQueue.create(historyCapacity);
        historyLock = new ReentrantReadWriteLock();
    }

    /**
     * Saves measurement to the history
     *
     * @param measurement
     */
    @Override
    public void putMeasurement(Measurement measurement) {
        historyLock.writeLock().lock();
        try {
            measurementHistory.add(measurement);
        } finally {
            historyLock.writeLock().unlock();
        }
    }

    /**
     * Returns given number n of measurements, or <= n, if there are not enough measurements yet
     *
     * @param n - history depth
     * @return measurements ordered by time they were made at desc
     */
    @Override
    public List<Measurement> getMeasurements(int n) {
        if (n <= 0)
            throw new IllegalArgumentException("Depth must be greater than 0");
        historyLock.readLock().lock();
        try {
            return measurementHistory.
                    stream()
                    .sorted(Comparator.comparing(Measurement::getMadeAt).reversed())
                    .limit(n)
                    .collect(Collectors.toList());
        } finally {
            historyLock.readLock().unlock();
        }
    }
}
