package com.wz.sensorserver.service;

import com.wz.sensors.proto.*;
import com.wz.sensorserver.interceptor.TokenAuthenticationInterceptor;
import com.wz.sensorserver.mq.MQConnectivity;
import com.wz.sensorserver.repository.ClientRepositoryInMemory;
import com.wz.sensorserver.repository.SensorRepositoryInMemory;
import com.wz.sensorserver.util.DummyClient;
import com.wz.sensorserver.util.DummySensor;
import com.wz.sensorserver.util.TestDataFactory;
import com.wz.sensorserver.util.ThreadCountReporter;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class DataTransmissionTest {
    static SensorService sensorService;
    static ClientService clientService;
    static Server server;
    static MQConnectivity mqConnectivity = new MQConnectivity();
    static int port;
    static AuthenticationService authenticationService = Mockito.spy(new AuthenticationService(Base64.getEncoder().encodeToString(UUID.randomUUID().toString().getBytes())));

    static Thread reporter;

    @BeforeAll
    public static void startServer() throws IOException {
        startThreadCountReporter();
        mqConnectivity.connect("localhost", 5672);
        SensorRepositoryInMemory sensorRepository = new SensorRepositoryInMemory();
        ClientRepositoryInMemory clientRepository = new ClientRepositoryInMemory();
        sensorService = new SensorService(mqConnectivity, authenticationService, sensorRepository);
        clientService = new ClientService(mqConnectivity, clientRepository, sensorRepository, authenticationService);
        server = ServerBuilder.forPort(8080)
                .intercept(new TokenAuthenticationInterceptor(clientRepository, sensorRepository, authenticationService))
                .addService(sensorService)
                .addService(clientService)
                .build()
                .start();
        port = server.getPort();
    }

    @AfterAll
    public static void tearDown() throws InterruptedException {
        mqConnectivity.close();
        server.shutdown();
        if (reporter != null) {
            reporter.interrupt();
        }
    }

    private static void startThreadCountReporter() {
        Thread reporter = new Thread(new ThreadCountReporter());
        reporter.start();
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0, 0, 100",
            "1, 1, 1, 0, 100",
            "1, 1, 1, 1, 100",
            "0, 1, 1, 0, 100",
            "10, 100, 60, 10, 2000"
    })
    public void testMultipleClientsAndMultipleSensorsSetup(int sensorsNum,
                                                           int clientsNum,
                                                           int clientsPerSensorNum,
                                                           int generatedDataSize,
                                                           int timeout
    ) throws InterruptedException {
        assertThat(clientsNum).isGreaterThanOrEqualTo(clientsPerSensorNum);
        //GIVEN
        //register sensor and subscribe clients. Sensors' generated data must be stored and later checked for the consistency
        List<DummySensor> dummySensors = Collections.synchronizedList(new ArrayList<>());
        List<DummyClient> dummyClients = Collections.synchronizedList(new ArrayList<>());
        Map<String, List<DummyClient>> sensorSubscriptions = new HashMap<>();
        Map<String, List<MeasurementRequest>> generatedDataBySensors = new HashMap<>();
        ExecutorService executorService = Executors.newCachedThreadPool();


        CountDownLatch clientsRegisteredLatch = new CountDownLatch(clientsNum);
        CountDownLatch sensorsRegisteredLatch = new CountDownLatch(sensorsNum);
        CountDownLatch subscriptionLatch = new CountDownLatch(sensorsNum * clientsPerSensorNum);
        CountDownLatch dataPublished = new CountDownLatch(sensorsNum);
        CountDownLatch dataStreamingCompleted = new CountDownLatch(sensorsNum);
        CountDownLatch dataReceivingCompleted = new CountDownLatch(clientsNum);

        IntStream.range(0, clientsNum).forEach(i -> {
            DummyClient dummyClient = new DummyClient(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), port);
            dummyClient.setOnRegistration(clientsRegisteredLatch::countDown);
            dummyClient.setOnComplete(dataReceivingCompleted::countDown);
            dummyClient.register();
            dummyClient.setOnSubscription(subscriptionLatch::countDown);
            dummyClient.setResponseStreamObserver(Mockito.spy(dummyClient.defaultResponseStreamObserver()));
            dummyClient.createBidirectionalStream();
            dummyClients.add(dummyClient);
        });
        IntStream.range(0, sensorsNum).forEach(i -> {
            DummySensor dummySensor = new DummySensor(TestDataFactory.randomSensor(), port);
            dummySensor.setOnRegistration(sensorsRegisteredLatch::countDown);
            dummySensor.registerSensor();
            dummySensor.setOnComplete(dataStreamingCompleted::countDown);
            dummySensor.setId(authenticationService.validateToken(dummySensor.getToken()).get("sensorId", String.class));
            dummySensors.add(dummySensor);
            //generate data that the sensor will transmit
            generatedDataBySensors.put(dummySensor.getId(),
                    Stream.generate(TestDataFactory::randomMeasurementRequest)
                            .limit(generatedDataSize)
                            .toList());
            // select random clients to subscribe
            sensorSubscriptions.put(dummySensor.getId(), new ArrayList<>());
            List<DummyClient> shuffled = new ArrayList<>(dummyClients);
            Collections.shuffle(shuffled);
            shuffled.stream()
                    .limit(clientsPerSensorNum)
                    .forEach(dummyClient -> {
                        sensorSubscriptions.get(dummySensor.getId()).add(dummyClient);
                        dummyClient.sendSubscribeRequest(SubscribeRequest
                                .newBuilder()
                                .setSensorId(dummySensor.getId())
                                .setDisconnect(false)
                                .build());
                    });
        });
        assertThat(sensorsRegisteredLatch.await(5000, TimeUnit.MILLISECONDS))
                .withFailMessage("Sensors are not registered")
                .isTrue();
        assertThat(clientsRegisteredLatch.await(5000, TimeUnit.MILLISECONDS))
                .withFailMessage("Clients are not registered")
                .isTrue();
        assertThat(subscriptionLatch.await(5000, TimeUnit.MILLISECONDS))
                .withFailMessage("Subscriptions are not ready")
                .isTrue();
        log.info("Setup is ready");

        //WHEN
        //and start streaming
        dummySensors.stream().parallel().forEach(dummySensor ->
                executorService.submit(() -> {
                    dummySensor.startStreaming();
                    for (MeasurementRequest measurementRequest : generatedDataBySensors.get(dummySensor.getId())
                    ) {
                        dummySensor.publishMeasurement(measurementRequest);
                        try {
                            Thread.sleep(ThreadLocalRandom.current().nextInt(100, 150));
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    dataPublished.countDown();
                })
        );

        //wait until data transmission is over and timeout for the client elapses
        assertThat(dataPublished.await(20, TimeUnit.SECONDS)).isTrue();
        dummySensors.forEach(DummySensor::completeStreaming);
        assertThat(dataStreamingCompleted.await(5000, TimeUnit.MILLISECONDS))
                .withFailMessage("Looks like server did not acknowledge that sensors' streams are over")
                .isTrue();
        Thread.sleep(timeout);// !!!!
        dummySensors.forEach(DummySensor::cleanUp);
        dummyClients.forEach(DummyClient::disconnect);
        dummyClients.forEach(DummyClient::cleanUp);
        assertThat(dataReceivingCompleted.await(5000, TimeUnit.MILLISECONDS))
                .withFailMessage("Not all subscribers received acknowledgement from server that they are disconnected")
                .isTrue();

        //THEN
        dummySensors.forEach(dummySensor -> {
            List<DummyClient> sensorsClients = sensorSubscriptions.get(dummySensor.getId());
            List<MeasurementResponse> measurementsThatMustBe = generatedDataBySensors.get(dummySensor.getId())
                    .stream()
                    .map(request -> MeasurementResponse.newBuilder()
                            .setSensorId(dummySensor.getId())
                            .setValue(request.getValue())
                            .setMadeAt(request.getMadeAt())
                            .build())
                    .toList();
            sensorsClients.forEach(dummyClient -> {
                ArgumentCaptor<SubscriptionResponse> measurementResponseCaptor = ArgumentCaptor.forClass(SubscriptionResponse.class);
                StreamObserver<SubscriptionResponse> responseStreamObserver = dummyClient.getResponseStreamObserver();
                Mockito.verify(responseStreamObserver, Mockito.atLeast(generatedDataSize)).onNext(measurementResponseCaptor.capture());
                List<MeasurementResponse> capturedMeasurements = measurementResponseCaptor
                        .getAllValues()
                        .stream()
                        .filter(cv -> cv.getResponseCase().equals(SubscriptionResponse.ResponseCase.MEASUREMENT))
                        .map(SubscriptionResponse::getMeasurement)
                        .toList();
                assertThat(capturedMeasurements.stream().toList())
                        .containsAll(measurementsThatMustBe);
            });
        });
    }
}
