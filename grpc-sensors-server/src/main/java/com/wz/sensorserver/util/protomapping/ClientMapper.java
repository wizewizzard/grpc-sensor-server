package com.wz.sensorserver.util.protomapping;

import com.wz.sensors.proto.ClientRegistrationRequest;
import com.wz.sensorserver.domain.Client;

public class ClientMapper {

    public Client mapRegistrationRequestToDomain(ClientRegistrationRequest request){
        return new Client(request.getLogin(), request.getPassword(), request.getPassword());
    }

}
