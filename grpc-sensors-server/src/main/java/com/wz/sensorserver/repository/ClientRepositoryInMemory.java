package com.wz.sensorserver.repository;

import com.wz.sensorserver.domain.Client;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientRepositoryInMemory implements ClientRepository {

    private final List<Client> clients;

    public ClientRepositoryInMemory() {
        clients = new CopyOnWriteArrayList<>();
    }

    @Override
    public void addClient(Client client) {
        clients.add(client);
    }

    @Override
    public Optional<Client> getClientByLogin(String login) {
        return clients.stream()
                .filter(client -> client.getLogin().equals(login))
                .findFirst();
    }

    @Override
    public boolean isUnique(Client client) {
        return clients.stream()
                .noneMatch(c -> c.getLogin().equals(client.getLogin()) || c.getEmail().equals(client.getEmail()));
    }
}
