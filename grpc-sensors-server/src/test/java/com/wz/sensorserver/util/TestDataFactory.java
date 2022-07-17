package com.wz.sensorserver.util;

import com.google.protobuf.Timestamp;
import com.wz.sensors.proto.MeasurementRequest;
import com.wz.sensorserver.domain.Measurement;
import com.wz.sensorserver.domain.Sensor;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestDataFactory {

    private static final List<String> tags = List.of("temperature", "voltage", "pressure", "opm", "humidity", "brightness");

    public static Measurement randomMeasurement() {
        long startSeconds = Instant.now().minus(Duration.ofDays(10)).getEpochSecond();
        long endSeconds = Instant.now().getEpochSecond();
        return new Measurement(ThreadLocalRandom.current().nextDouble(),
                Instant.ofEpochSecond(ThreadLocalRandom.current().nextLong(startSeconds, endSeconds)));
    }

    public static MeasurementRequest randomMeasurementRequest() {
        long startSeconds = Instant.now().minus(Duration.ofDays(10)).getEpochSecond();
        long endSeconds = Instant.now().getEpochSecond();
        return MeasurementRequest.newBuilder()
                .setMadeAt(Timestamp.newBuilder()
                        .setSeconds(ThreadLocalRandom.current().nextLong(startSeconds, endSeconds))
                        .setNanos(ThreadLocalRandom.current().nextInt(0, 1_000_000_000))
                        .build())
                .setValue(ThreadLocalRandom.current().nextDouble())
                .build();
    }

    public static Sensor randomSensor() {
        return Sensor
                .builder()
                .id(UUID.randomUUID())
                .name(UUID.randomUUID().toString())
                .location(UUID.randomUUID().toString())
                .tags(Stream.generate(UUID::randomUUID)
                        .map(UUID::toString)
                        .limit(ThreadLocalRandom.current().nextInt(1, 5))
                        .collect(Collectors.toList()))
                .build();
    }

    @Test
    public void asdsad() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.fromString(uuid1.toString());

        Map<UUID, Integer> map = new HashMap<>();
        map.put(uuid1, 25);
        Integer integer = map.get(uuid2);

        System.out.println(integer);
    }

}
