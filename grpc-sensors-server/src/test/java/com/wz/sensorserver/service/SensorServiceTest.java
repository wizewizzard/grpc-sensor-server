package com.wz.sensorserver.service;

import com.google.protobuf.Empty;
import com.rabbitmq.client.BuiltinExchangeType;
import com.wz.sensors.proto.*;
import com.wz.sensorserver.domain.Measurement;
import com.wz.sensorserver.domain.Sensor;
import com.wz.sensorserver.interceptor.TokenAuthenticationInterceptor;
import com.wz.sensorserver.mq.MQConnectivity;
import com.wz.sensorserver.repository.ClientRepository;
import com.wz.sensorserver.repository.SensorRepository;
import com.wz.sensorserver.service.streamobserver.NoopStreamObserver;
import com.wz.sensorserver.util.TestDataFactory;
import com.wz.sensorserver.util.protomapping.MeasurementMapper;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensorServiceTest {
    static SensorService underTest;
    static SensorRepository sensorRepository = mock(SensorRepository.class);
    static ClientRepository clientRepository = mock(ClientRepository.class);
    static AuthenticationService authenticationService = mock(AuthenticationService.class);
    static MQConnectivity mqConnectivity = mock(MQConnectivity.class);
    static Server server;
    static int port;
    static ManagedChannel channel;
    static SensorServiceGrpc.SensorServiceBlockingStub blockingStub;
    static SensorServiceGrpc.SensorServiceStub asyncStub;

    @BeforeAll
    public static void startServer() throws IOException {
        underTest = new SensorService(mqConnectivity, authenticationService, sensorRepository);
        server = ServerBuilder.forPort(8091)
                .intercept(new TokenAuthenticationInterceptor(clientRepository, sensorRepository, authenticationService))
                .addService(underTest)
                .build()
                .start();
        port = server.getPort();
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .directExecutor()
                .build();
        blockingStub = SensorServiceGrpc.newBlockingStub(channel);
        asyncStub = SensorServiceGrpc.newStub(channel);
    }

    @AfterAll
    public static void tearDown() throws InterruptedException {
        mqConnectivity.close();
        server.shutdown();
        channel.shutdown();
    }

    @Test
    public void sensorRegistrationCreatesSensorAndReturnsToken() {
        Collection<String> tags = List.of("cave", "gas-level");
        SensorRegistrationRequest sensorRegister = SensorRegistrationRequest.newBuilder()
                .setName("cave-gas-sensor")
                .setLocation("Diamond cave in Siberia")
                .addAllTags(tags)
                .build();
        ArgumentCaptor<Sensor> capturedSensor = ArgumentCaptor.forClass(Sensor.class);
        when(sensorRepository.checkIfExists(any(Sensor.class))).thenReturn(false);
        when(authenticationService.generateToken(any(Map.class))).thenReturn("generated token");

        SensorRegistrationResponse response = blockingStub.registerSensor(sensorRegister);

        verify(sensorRepository).addSensor(capturedSensor.capture());
        assertThat(response.getToken()).isEqualTo("generated token");
        assertThat(response.getId()).isNotEmpty();
        assertThat(capturedSensor).isNotNull();
        Sensor sensor = capturedSensor.getValue();
        assertThat(sensor.getId()).isNotNull();
        assertThat(sensor.getName()).isEqualTo(sensorRegister.getName());
        assertThat(sensor.getLocation()).isEqualTo(sensorRegister.getLocation());
        assertThat(sensor.getTags()).containsExactlyInAnyOrderElementsOf(tags);
    }

    @Test
    public void serviceReturnsErrorWhenTryingToRegisterASensorThatAlreadyExists() {
        SensorRegistrationRequest sensorRegister = SensorRegistrationRequest.newBuilder()
                .setName("Bubu")
                .setLocation("Bebe")
                .build();
        when(sensorRepository.checkIfExists(any(Sensor.class))).thenReturn(true);

        Exception exception = catchException(() -> blockingStub.registerSensor(sensorRegister));

        assertThat(exception).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) exception).getStatus().getCode()).isEqualTo(Status.ALREADY_EXISTS.getCode());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10, 100})
    public void testSensorMeasurementStream(int generatedDataSize) throws InterruptedException, IOException {
        final String sensorId = UUID.randomUUID().toString();
        Sensor sensorMock = mock(Sensor.class);
        Claims claimsMock = mock(Claims.class);
        com.rabbitmq.client.Channel channelMock = mock(com.rabbitmq.client.Channel.class);
        ArgumentCaptor<Measurement> sensorArgumentCaptor = ArgumentCaptor.forClass(Measurement.class);

        StreamObserver<Empty> completionStreamObserverSpy = Mockito.spy(new NoopStreamObserver<>());
        when(claimsMock.get("sensorId", String.class)).thenReturn(sensorId);
        when(authenticationService.validateToken(any())).thenReturn(claimsMock);
        when(sensorRepository.getSensorById(any(UUID.class))).thenReturn(Optional.of(sensorMock));
        when(sensorMock.getOnlineStatus()).thenReturn(Sensor.OnlineStatus.OFFLINE);
        when(sensorMock.getId()).thenReturn(UUID.fromString(sensorId));
        when(mqConnectivity.newChannel()).thenReturn(channelMock);

        StreamObserver<MeasurementRequest> measurementStreamObserver = asyncStub.sendMeasurements(completionStreamObserverSpy);
        List<MeasurementRequest> measurementRequests = Stream.generate(TestDataFactory::randomMeasurementRequest).limit(generatedDataSize).toList();

        for (MeasurementRequest m : measurementRequests) {
            measurementStreamObserver.onNext(m);
        }
        measurementStreamObserver.onCompleted();
        Thread.sleep(500);

        MeasurementMapper mapper = new MeasurementMapper();
        verify(completionStreamObserverSpy, times(1)).onCompleted();
        verify(sensorMock, times(generatedDataSize)).putMeasurement(sensorArgumentCaptor.capture());
        verify(channelMock, times(1)).exchangeDeclare(sensorId, BuiltinExchangeType.FANOUT, false, false, null);
        assertThat(sensorArgumentCaptor.getAllValues()).containsExactlyInAnyOrderElementsOf(
                measurementRequests
                        .stream()
                        .map(mapper::mapRequestToDomain)
                        .collect(Collectors.toList())
        );
    }

    @Test
    public void testMeasurementStreamEndsWithErrorIfSensorIsNotRegistered() throws InterruptedException {
        Claims claimsMock = mock(Claims.class);
        StreamObserver<Empty> statObserverSpy = Mockito.spy(new NoopStreamObserver<>());

        when(claimsMock.get("sensorId", String.class)).thenReturn(UUID.randomUUID().toString());
        when(authenticationService.validateToken(any())).thenReturn(claimsMock);
        when(sensorRepository.getSensorById(any(UUID.class))).thenReturn(Optional.empty());

        StreamObserver<MeasurementRequest> measurementStreamObserver = asyncStub.sendMeasurements(statObserverSpy);
        List<MeasurementRequest> measurements = Stream.generate(TestDataFactory::randomMeasurementRequest).limit(5).toList();

        for (MeasurementRequest m : measurements) {
            measurementStreamObserver.onNext(m);
        }
        measurementStreamObserver.onCompleted();
        Thread.sleep(200);

        Mockito.verify(statObserverSpy, times(0)).onCompleted();
        Mockito.verify(statObserverSpy, times(1)).onError(any(Throwable.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 20, 50, 60})
    public void testGetSensorHistory(int generatedDataSize) {
        final MeasurementMapper mapper = new MeasurementMapper();
        int reqDepth = 50;
        int historyCapacity = 50;
        UUID uuid = UUID.randomUUID();
        Sensor sensorStub = new Sensor(uuid, "Test Sensor", "Cuba", List.of("Temperature", "Reactor"), historyCapacity);
        List<Measurement> measurements = DoubleStream
                .generate(Math::random)
                .limit(generatedDataSize)
                .mapToObj(val -> new Measurement(val, Instant.now())).toList();
        measurements.forEach(sensorStub::putMeasurement);
        when(sensorRepository.getSensorById(eq(uuid))).thenReturn(Optional.of(sensorStub));

        SensorHistoryResponse historyForSensor = blockingStub
                .getHistoryForSensor(SensorHistoryRequest
                        .newBuilder()
                        .setSensorId(uuid.toString())
                        .setDepth(reqDepth)
                        .build());
        SensorOnlineStatus sensorStatus = historyForSensor.getSensorStatus();
        List<MeasurementResponse> measurementsList = historyForSensor.getMeasurementsList();

        assertThat(sensorStatus).isEqualTo(SensorOnlineStatus.SENSOR_OFFLINE);
        assertThat(measurementsList)
                .hasSizeLessThanOrEqualTo(Math.min(generatedDataSize, reqDepth))
                .isSortedAccordingTo(Comparator.comparing((MeasurementResponse m) -> Instant.ofEpochSecond(m.getMadeAt().getSeconds(), m.getMadeAt().getNanos())).reversed())
                .containsAll(measurements
                        .stream()
                        .skip(generatedDataSize > historyCapacity ? generatedDataSize - historyCapacity : 0)
                        .map(mapper::mapDomainToResponse)
                        .collect(Collectors.toList()));
    }

    @Test
    public void testGetSensorHistoryTriggersErrorWhenIdNotAssociatedWithSensorIsUsed() {
        int reqDepth = 50;
        UUID uuid = UUID.randomUUID();
        when(sensorRepository.getSensorById(eq(uuid))).thenReturn(Optional.empty());

        Exception exception = catchException(() -> blockingStub
                .getHistoryForSensor(SensorHistoryRequest
                        .newBuilder()
                        .setSensorId(uuid.toString())
                        .setDepth(reqDepth)
                        .build()));

        assertThat(exception)
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("Sensor with given id does not exist");

    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10, 100})
    public void getSensorsListTest(int sensorsCount) {
        when(sensorRepository.findSensorsByTags(anyCollection())).thenReturn(Stream.generate(TestDataFactory::randomSensor).limit(sensorsCount).toList());
        SearchTagsRequest searchTagsRequest = SearchTagsRequest.newBuilder().build();
        Iterator<SensorInfoResponse> sensorInfoResponseIterator = blockingStub.getSensors(searchTagsRequest);
        int count = 0;

        while (sensorInfoResponseIterator.hasNext()) {
            sensorInfoResponseIterator.next();
            count++;
        }

        assertThat(count).isEqualTo(sensorsCount);
    }

    /*@Test
    public void searchSensorsByTags() {
        //when(sensorRepository.findSensorsByTags(anyCollection())).thenCallRealMethod();
        List<Sensor> sensors = Stream.generate(TestDataFactory::randomSensor).limit(10).collect(Collectors.toList());

        when(sensorRepository.findSensorsByTags(Collections.emptyList())).thenReturn(sensors);
        when(sensorRepository.findSensorsByTags(List.of("t1"))).thenReturn(List.of(sensors.get(0), sensors.get(3)));
        when(sensorRepository.findSensorsByTags(List.of("t2", "t3"))).thenReturn(List.of(sensors.get(0), sensors.get(3)));
        when(sensorRepository.findSensorsByTags(List.of("t1", "t3"))).thenReturn(List.of(sensors.get(0), sensors.get(3)));

        SearchTagsRequest searchNoTagRequest = SearchTagsRequest.newBuilder().build();
        SearchTagsRequest searchTagT1Request = SearchTagsRequest.newBuilder().addTags("t1").build();
        SearchTagsRequest searchTagT2T3Request = SearchTagsRequest.newBuilder().addTags("t2").addTags("t3").build();
        SearchTagsRequest searchTagT1T3Request = SearchTagsRequest.newBuilder().addTags("t1").addTags("t3").build();

        List<SensorInfoResponse> foundForNoTag = new ArrayList<>();
        List<SensorInfoResponse> foundForT1 = new ArrayList<>();
        List<SensorInfoResponse> foundForT2T3 = new ArrayList<>();
        List<SensorInfoResponse> foundForT1T3 = new ArrayList<>();
        blockingStub.getSensors(searchNoTagRequest).forEachRemaining(foundForNoTag::add);
        blockingStub.getSensors(searchTagT1Request).forEachRemaining(foundForT1::add);
        blockingStub.getSensors(searchTagT2T3Request).forEachRemaining(foundForT2T3::add);
        blockingStub.getSensors(searchTagT1T3Request).forEachRemaining(foundForT1T3::add);

        assertThat(foundForNoTag).hasSize(4);
        assertThat(foundForT1).hasSize(2);
        assertThat(foundForT2T3).hasSize(3);
        assertThat(foundForT1T3).hasSize(2);
    }*/

}