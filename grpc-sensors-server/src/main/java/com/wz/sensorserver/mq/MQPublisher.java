package com.wz.sensorserver.mq;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class MQPublisher<T extends Serializable> implements Publisher<T>{

    private final Channel channel;
    private final Lock channelLock;
    private final String exchangeName;
    private final String routingKey;

    public MQPublisher(Channel channel, String exchangeName, String routingKey) {
        this.channel = channel;
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
        channelLock = new ReentrantLock();
    }

    @Override
    public void publishMessage(T message) {
        log.trace("Publishing message to the exchange: {} and rk: {}", exchangeName, routingKey);
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             ObjectOutputStream outputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            outputStream.writeObject(message);
            outputStream.flush();
            channelLock.lock();
            try{
                channel.basicPublish(exchangeName, routingKey, null, byteArrayOutputStream.toByteArray());
            }
            finally {
                channelLock.unlock();
            }
        } catch (IOException e) {
            log.error("Unable to serialize the object and publish it to the exchange: {} and rk: {}", exchangeName, routingKey);
            throw new RuntimeException("Unable to serialize the object");
        }
    }

    @Override
    public void cleanUp() {
        channelLock.lock();
        try{
            channel.close();
        }
        catch (IOException | TimeoutException e) {
            log.error("Error when closing amqp channel", e);
            //throw new RuntimeException(e);
        }
        finally {
            channelLock.unlock();
        }
    }
}
