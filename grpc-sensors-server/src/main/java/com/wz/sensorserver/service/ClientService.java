package com.wz.sensorserver.service;

import com.wz.sensors.proto.*;
import com.wz.sensorserver.domain.Client;
import com.wz.sensorserver.mq.MQConnectivity;
import com.wz.sensorserver.repository.ClientRepository;
import com.wz.sensorserver.repository.SensorRepository;
import com.wz.sensorserver.service.streamobserver.SubscriberStreamObserver;
import com.wz.sensorserver.util.protomapping.ClientMapper;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class ClientService extends SensorClientServiceGrpc.SensorClientServiceImplBase {
    private final ClientRepository clientRepository;
    private final SensorRepository sensorRepository;
    private final AuthenticationService authenticationService;
    private final MQConnectivity mqConnectivity;
    private final ClientMapper clientMapper;

    public ClientService(MQConnectivity mqConnectivity,
                         ClientRepository clientRepository,
                         SensorRepository sensorRepository,
                         AuthenticationService authenticationService) {
        Objects.requireNonNull(clientRepository);
        Objects.requireNonNull(sensorRepository);
        Objects.requireNonNull(authenticationService);
        this.mqConnectivity = mqConnectivity;
        this.clientRepository = clientRepository;
        this.sensorRepository = sensorRepository;
        this.authenticationService = authenticationService;
        clientMapper = new ClientMapper();
    }

    @Override
    public void registerClient(ClientRegistrationRequest request, StreamObserver<TokenResponse> responseObserver) {
        Client client = clientMapper.mapRegistrationRequestToDomain(request);
        if (clientRepository.isUnique(client)) {
            String token = authenticationService.generateToken(Map.of("login", client.getLogin()));
            clientRepository.addClient(client);
            log.debug("Client with login {} was registered", client.getLogin());
            responseObserver.onNext(TokenResponse.newBuilder().setToken(token).build());
            responseObserver.onCompleted();
        } else {
            log.debug("Client was not registered. Already exists.");
            Status status = Status.ALREADY_EXISTS
                    .withDescription("Client with given login or email already exists");
            responseObserver.onError(status.asRuntimeException());
        }
    }

    @Override
    public void loginClient(ClientLoginRequest request, StreamObserver<TokenResponse> responseObserver) {
        Optional<Client> clientOptional = clientRepository.getClientByLogin(request.getLogin());
        if (clientOptional.isPresent()) {
            Client client = clientOptional.get();
            if (Objects.equals(client.getPassword(), request.getPassword())) {
                String token = authenticationService.generateToken(Map.of("login", client.getLogin()));
                responseObserver.onNext(TokenResponse.newBuilder().setToken(token).build());
                responseObserver.onCompleted();
                return;
            }
        }
        log.info("Wrong credentials were given");
        Status status = Status.ALREADY_EXISTS
                .withDescription("Client with given credentials does not exist");
        responseObserver.onError(status.asRuntimeException());
    }

    @Override
    public StreamObserver<SubscribeRequest> subscribeOnSensor(StreamObserver<SubscriptionResponse> responseObserver) {
        return new SubscriberStreamObserver(mqConnectivity, responseObserver, sensorRepository);
    }
}
