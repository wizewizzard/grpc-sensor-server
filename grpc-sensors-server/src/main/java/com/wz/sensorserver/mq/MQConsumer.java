package com.wz.sensorserver.mq;

import com.rabbitmq.client.*;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.concurrent.TimeoutException;

/**
 * Consumes, deserializes messages from mq and triggers given method
 * @param <T>
 */
@Slf4j
public class MQConsumer<T extends Serializable> extends DefaultConsumer {
    private final java.util.function.Consumer<T> onMessageReceived;
    public MQConsumer(Channel channel,
                      java.util.function.Consumer<T> onMessageReceived
                    ) {
        super(channel);
        this.onMessageReceived = onMessageReceived;
    }

    @Override
    public void handleDelivery(String consumerTag,
                               Envelope envelope,
                               AMQP.BasicProperties properties,
                               byte[] body){
        log.trace("Message received. Trying to handle it");
        try( ByteArrayInputStream inputStream = new ByteArrayInputStream(body);
            ObjectInputStream objectInputStream = new ObjectInputStream(inputStream)){
            T message = (T) objectInputStream.readObject();
            log.debug("Messaged deserialized. Message: {}", message);
            getChannel().basicAck(envelope.getDeliveryTag(), false);
            onMessageReceived.accept(message);
        }
        catch (IOException | ClassNotFoundException exception){
            log.error("Can not deserialize the message");
            throw new RuntimeException("Error when deserializing a message", exception);
        }
        catch (AlreadyClosedException ignored){
            log.warn("Message was received but channel is already closed");
        }
    }

    public void cleanUp(){
        try {
            getChannel().close();
        } catch (IOException | TimeoutException e) {
            log.error("Error when closing amqp channel", e);
        }
    }
}
