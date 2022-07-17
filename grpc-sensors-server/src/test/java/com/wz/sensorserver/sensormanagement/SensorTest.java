package com.wz.sensorserver.sensormanagement;


import com.wz.sensorserver.domain.Measurement;
import com.wz.sensorserver.domain.Sensor;
import com.wz.sensorserver.util.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class SensorTest {

    @Test
    public void testHistoryConsistencyWhenMultipleThreadsAccessIt() throws InterruptedException {
        int historyCapacity = 50;
        final Sensor sensor = Sensor
                .builder()
                .historyCapacity(historyCapacity)
                .build();
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        List<Measurement> measurements1 = Stream.generate(TestDataFactory::randomMeasurement).limit(7).toList();
        List<Measurement> measurements2 = Stream.generate(TestDataFactory::randomMeasurement).limit(25).toList();
        List<Measurement> measurements3 = Stream.generate(TestDataFactory::randomMeasurement).limit(18).toList();

        Runnable p1 = () -> measurements1.forEach(sensor::putMeasurement);
        Runnable p2 = () -> measurements2.forEach(sensor::putMeasurement);
        Runnable p3 = () -> measurements3.forEach(sensor::putMeasurement);

        executorService.submit(p1);
        executorService.submit(p2);
        executorService.submit(p3);

        executorService.shutdown();
        boolean terminated = executorService.awaitTermination(1000, TimeUnit.MILLISECONDS);
        if(!terminated){
            executorService.shutdownNow();
        }

        assertThat(terminated).isTrue();
        assertThat(sensor.getMeasurements(historyCapacity))
                .hasSize(historyCapacity)
                .isSortedAccordingTo(Comparator.comparing(Measurement::getMadeAt).reversed())
                .containsAll(measurements1)
                .containsAll(measurements2)
                .containsAll(measurements3);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7})
    public void testHistoryConsistencyWhenItHasNoOrLessThanRequiredElements(int generatedDataSize) throws InterruptedException {
        int historyCapacity = 50;
        final Sensor sensor = Sensor
                .builder()
                .historyCapacity(historyCapacity)
                .build();
        List<Measurement> measurements1 = Stream.generate(TestDataFactory::randomMeasurement).limit(generatedDataSize).toList();

        measurements1.forEach(sensor::putMeasurement);

        assertThat(sensor.getMeasurements(historyCapacity))
                .hasSize(generatedDataSize)
                .isSortedAccordingTo(Comparator.comparing(Measurement::getMadeAt).reversed())
                .containsAll(measurements1);
    }


    @Test
    public void testHistoryConsistencyWhenItOverflows(){
        int n1 = 50;
        int n2 = 18;
        int historyCapacity = 50;
        final Sensor sensor = Sensor
                .builder()
                .historyCapacity(historyCapacity)
                .build();
        List<Measurement> measurements1 = Stream.generate(TestDataFactory::randomMeasurement).limit(n1).toList();
        List<Measurement> measurements2 = Stream.generate(TestDataFactory::randomMeasurement).limit(n2).toList();
        measurements1.forEach(sensor::putMeasurement);
        measurements2.forEach(sensor::putMeasurement);

        List<Measurement> measurementsRetained = measurements1.stream().skip(n2).toList();

        assertThat(sensor.getMeasurements(historyCapacity))
                .hasSize(historyCapacity)
                .isSortedAccordingTo(Comparator.comparing(Measurement::getMadeAt).reversed())
                .containsAll(measurementsRetained)
                .containsAll(measurements2);

    }
}