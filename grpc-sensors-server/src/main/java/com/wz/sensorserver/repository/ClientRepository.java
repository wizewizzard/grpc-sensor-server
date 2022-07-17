package com.wz.sensorserver.repository;

import com.wz.sensorserver.domain.Client;

import java.util.Optional;

public interface ClientRepository {
    void addClient(Client client);

    Optional<Client> getClientByLogin(String login);

    boolean isUnique(Client client);

}
