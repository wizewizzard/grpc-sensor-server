package com.wz.sensorserver.service;

import com.wz.sensors.proto.ClientLoginRequest;
import com.wz.sensors.proto.ClientRegistrationRequest;
import com.wz.sensors.proto.SensorClientServiceGrpc;
import com.wz.sensors.proto.TokenResponse;
import com.wz.sensorserver.domain.Client;
import com.wz.sensorserver.interceptor.TokenAuthenticationInterceptor;
import com.wz.sensorserver.mq.MQConnectivity;
import com.wz.sensorserver.repository.ClientRepository;
import com.wz.sensorserver.repository.SensorRepository;
import io.grpc.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientServiceTest {

    private static ClientService underTest;
    private static Server server;
    private static ManagedChannel channel;
    private static final AuthenticationService authenticationService = mock(AuthenticationService.class);
    static MQConnectivity mqConnectivity = mock(MQConnectivity.class);
    private static final ClientRepository clientRepository = mock(ClientRepository.class);
    private static final SensorRepository sensorRepository = mock(SensorRepository.class);
    private static int port;

    @BeforeAll
    public static void setUp() throws IOException {
        underTest = new ClientService(mqConnectivity, clientRepository, sensorRepository, authenticationService);
        server = ServerBuilder.forPort(8090)
                .intercept(new TokenAuthenticationInterceptor(clientRepository, sensorRepository, authenticationService))
                .addService(underTest)
                .build()
                .start();
        port = server.getPort();
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .directExecutor()
                .build();
    }

    @AfterAll
    public static void tearDown() {
        server.shutdown();
        channel.shutdown();
    }

    @Test
    public void testClientRegistration() {
        String tokenMock = "MockedToken";
        ClientRegistrationRequest request = ClientRegistrationRequest
                .newBuilder()
                .setLogin("Foo")
                .setEmail("Bar@email.com")
                .setPassword("12345")
                .build();
        SensorClientServiceGrpc.SensorClientServiceBlockingStub blockingStub = SensorClientServiceGrpc.newBlockingStub(channel);
        when(clientRepository.isUnique(any(Client.class))).thenReturn(true);
        when(authenticationService.generateToken(any(Map.class))).thenReturn(tokenMock);

        TokenResponse tokenResponse = blockingStub.registerClient(request);

        assertThat(tokenResponse.getToken()).isEqualTo(tokenMock);
    }

    @Test
    public void testClientRegistrationUniquenessFailure() {
        ClientRegistrationRequest request = ClientRegistrationRequest
                .newBuilder()
                .setLogin("Foo")
                .setEmail("Bar@email.com")
                .setPassword("12345")
                .build();
        SensorClientServiceGrpc.SensorClientServiceBlockingStub blockingStub = SensorClientServiceGrpc.newBlockingStub(channel);
        when(clientRepository.isUnique(any(Client.class))).thenReturn(false);

        Exception exception = catchException(() -> blockingStub.registerClient(request));

        assertThat(exception)
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("Client with given login or email already exists");
    }

    @Test
    public void testLoginWhenPasswordsAreEqual() {
        String tokenMock = "MockedToken";
        ClientLoginRequest request = ClientLoginRequest
                .newBuilder()
                .setLogin("Foo")
                .setPassword("12345")
                .build();
        Client client = new Client("Foo", "some@email.com", "12345");
        when(clientRepository.getClientByLogin("Foo")).thenReturn(Optional.of(client));

        when(authenticationService.generateToken(any(Map.class))).thenReturn(tokenMock);

        SensorClientServiceGrpc.SensorClientServiceBlockingStub blockingStub = SensorClientServiceGrpc.newBlockingStub(channel);
        TokenResponse tokenResponse = blockingStub.loginClient(request);

        assertThat(tokenResponse.getToken()).isEqualTo(tokenMock);
    }

    @Test
    public void testLoginWhenPasswordsAreNotEqual() {
        ClientLoginRequest request = ClientLoginRequest
                .newBuilder()
                .setLogin("Foo")
                .setPassword("12345")
                .build();
        Client client = new Client("Foo", "some@email.com", "54321");
        when(clientRepository.getClientByLogin("Foo")).thenReturn(Optional.of(client));

        SensorClientServiceGrpc.SensorClientServiceBlockingStub blockingStub = SensorClientServiceGrpc.newBlockingStub(channel);
        Exception exception = catchException(() -> blockingStub.loginClient(request));

        assertThat(exception)
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("Client with given credentials does not exist");
    }
}