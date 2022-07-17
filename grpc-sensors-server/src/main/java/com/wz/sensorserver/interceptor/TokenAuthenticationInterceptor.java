package com.wz.sensorserver.interceptor;

import com.wz.sensorserver.constant.Constants;
import com.wz.sensorserver.exception.AuthenticationException;
import com.wz.sensorserver.repository.ClientRepository;
import com.wz.sensorserver.repository.SensorRepository;
import com.wz.sensorserver.service.AuthenticationService;
import io.grpc.*;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

@Slf4j
public class TokenAuthenticationInterceptor implements ServerInterceptor {

    private final ClientRepository clientRepository;
    private final SensorRepository sensorRepository;
    private final AuthenticationService authenticationService;

    public TokenAuthenticationInterceptor(ClientRepository clientRepository, SensorRepository sensorRepository, AuthenticationService authenticationService) {
        this.clientRepository = clientRepository;
        this.sensorRepository = sensorRepository;
        this.authenticationService = authenticationService;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {

        if(call.getMethodDescriptor().getFullMethodName().equals("sensors.SensorService/SendMeasurements")){
            Status status;
            try{
                String authorizationKey = headers.get(Metadata.Key.of("Authorization", ASCII_STRING_MARSHALLER));
                Claims claims = authenticationService.validateToken(authorizationKey);
                String sensorId = claims.get("sensorId", String.class);
                if(sensorId != null && sensorRepository.getSensorById(UUID.fromString(sensorId)).isPresent()) {
                    log.debug("Sensor id extracted from token: {}", sensorId);
                    Context newContext = Context.current().withValue(Constants.SENSOR_ID_CONTEXT_KEY, sensorId);
                    return Contexts.interceptCall(newContext, call, headers, next);
                }
                else{
                    throw new AuthenticationException("Wrong token provided");
                }
            }
            catch (AuthenticationException authenticationException){
                log.debug("Authentication was not successful: {}", authenticationException.getMessage());
                status = Status.UNAUTHENTICATED.withDescription(authenticationException.getMessage());
            }
            call.close(status, headers);
            return new ServerCall.Listener<>() {
                // noop
            };
        }
        else if(call.getMethodDescriptor().getFullMethodName().equals("sensors.SensorClientService/SubscribeOnSensor")){
            Status status;
            try{
                String authorizationKey = headers.get(Metadata.Key.of("Authorization", ASCII_STRING_MARSHALLER));
                Claims claims = authenticationService.validateToken(authorizationKey);
                String clientLogin = claims.get("login", String.class);
                if(clientLogin != null && clientRepository.getClientByLogin(clientLogin).isPresent())
                {
                    log.debug("Client login extracted from token: {}", clientLogin);
                    Context newContext = Context.current().withValue(Constants.CLIENT_LOGIN_CONTEXT_KEY,  clientLogin);
                    return Contexts.interceptCall(newContext, call, headers, next);
                }
                else{
                    throw new AuthenticationException("Wrong token provided");
                }
            }
            catch (AuthenticationException authenticationException){
                log.debug("Authentication was not successful: {}", authenticationException.getMessage());
                status = Status.UNAUTHENTICATED.withDescription(authenticationException.getMessage());
            }
            call.close(status, headers);
            return new ServerCall.Listener<>() {
                // noop
            };
        }
        return next.startCall(call, headers);


    }
}
