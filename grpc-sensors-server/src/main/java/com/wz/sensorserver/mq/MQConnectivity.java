package com.wz.sensorserver.mq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.util.Objects;

public class MQConnectivity {
    private Connection connection;

    public MQConnectivity(){
    }

    /**
     * Opens connection with a message broker
     * @param host
     * @param port
     */
    public void connect(String host, int port){
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        try{
            connection = factory.newConnection();
        }
        catch (Exception exception){
            throw new RuntimeException("Unable to connect to message queue. Host: %s and port %d".formatted(host, port));
        }
    }

    /**
     * Closes connection with a message broker
     */
    public void close(){
        if(connection != null ){
            try{
                connection.close();
            }
            catch (Exception exception){
                throw new RuntimeException("Could not close connection with message queue");
            }
        }
    }

    /**
     * Allocates a new channel from the established connection
     * @return
     */
    public Channel newChannel(){
        Objects.requireNonNull(connection);
        try{
            return connection.createChannel();
        }
        catch (Exception exception){
            throw new RuntimeException("Unable to create a new channel");
        }
    }


}
