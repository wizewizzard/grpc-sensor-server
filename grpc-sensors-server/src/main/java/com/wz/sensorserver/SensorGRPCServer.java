package com.wz.sensorserver;

import com.wz.sensorserver.interceptor.TokenAuthenticationInterceptor;
import com.wz.sensorserver.mq.MQConnectivity;
import com.wz.sensorserver.repository.ClientRepository;
import com.wz.sensorserver.repository.ClientRepositoryInMemory;
import com.wz.sensorserver.repository.SensorRepository;
import com.wz.sensorserver.repository.SensorRepositoryInMemory;
import com.wz.sensorserver.service.AuthenticationService;
import com.wz.sensorserver.service.ClientService;
import com.wz.sensorserver.service.SensorService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;

import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

@Slf4j
public class SensorGRPCServer {
    public static void main(String[] args) throws IOException, InterruptedException, ParseException {
        Options options = new Options();

        Option serverPortOption = new Option("p", true, "Server port");
        serverPortOption.setRequired(false);
        options.addOption(serverPortOption);

        Option RMQHostOption = new Option("mqh", true, "Message Queue port");
        RMQHostOption.setRequired(false);
        options.addOption(RMQHostOption);

        Option RMQPortOption = new Option("mqp", true, "Message Queue port");
        RMQPortOption.setRequired(false);
        options.addOption(RMQPortOption);

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        int serverPort =  Integer.parseInt(cmd.getOptionValue("p", "8090"));
        String rmqHost =  cmd.getOptionValue("mqh", "localhost");
        int rmqPort =  Integer.parseInt(cmd.getOptionValue("mqp", "5672"));


        MQConnectivity mqConnectivity = new MQConnectivity();
        mqConnectivity.connect(rmqHost, rmqPort);
        AuthenticationService authenticationService = new AuthenticationService(Base64.getEncoder().encodeToString(UUID.randomUUID().toString().getBytes()));
        SensorRepository sensorRepository = new SensorRepositoryInMemory();
        ClientRepository clientRepository = new ClientRepositoryInMemory();
        SensorService sensorService = new SensorService(mqConnectivity, authenticationService, sensorRepository);
        ClientService clientService = new ClientService(mqConnectivity, clientRepository, sensorRepository, authenticationService);

        log.info("Starting server on port: {}", serverPort);
        Server server = ServerBuilder.forPort(serverPort)
                .intercept(new TokenAuthenticationInterceptor(clientRepository, sensorRepository, authenticationService))
                .addService(sensorService)
                .addService(clientService)
                .build()
                .start();
        log.info("Server started");
        server.awaitTermination();
        log.info("Server stopped");
    }
}
