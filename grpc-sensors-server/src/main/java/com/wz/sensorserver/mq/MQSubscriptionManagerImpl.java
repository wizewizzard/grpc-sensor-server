package com.wz.sensorserver.mq;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.wz.sensorserver.exception.SubscriptionException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.Serializable;
import java.util.function.Consumer;


@Slf4j
public class MQSubscriptionManagerImpl<T extends Serializable> implements MQSubscriptionManager<String, T>{
    private final MQConnectivity mqConnectivity;
    private final ClientSubscription clientSubscription;

    public MQSubscriptionManagerImpl(MQConnectivity mqConnectivity) {
        this.mqConnectivity = mqConnectivity;
        this.clientSubscription = new ClientSubscription();
    }

    @Override
    public void trySubscribe(String key, Consumer<T> messageConsumer) throws SubscriptionException {
        Channel channel = mqConnectivity.newChannel();
        try{
            if(!clientSubscription.isSubscribed(key)){
                channel.exchangeDeclare(key, BuiltinExchangeType.FANOUT, false, false, null);
                String queue = channel.queueDeclare().getQueue(); // creates self - deletable queue
                channel.queueBind(queue, key, "");
                log.trace("Created a mq chain. exchange: {}, queue: {}", key, queue);
                MQConsumer<T> MQConsumer = new MQConsumer<>(channel, messageConsumer);
                channel.basicConsume(queue, MQConsumer);
                clientSubscription.subscribeConsumer(key, MQConsumer);
            }
            else {
                throw new SubscriptionException("Client is already subscribed");
            }
        }
        catch(IOException ioException){
            log.error("Error when creating exchange, queue and binding");
            throw new SubscriptionException("Unable ");
        }
    }

    @Override
    public void tryUnsubscribe(String key) throws SubscriptionException {
        if(clientSubscription.isSubscribed(key)){
            clientSubscription.unsubscribeConsumer(key);
        }
        else{
            throw new SubscriptionException("Client is not subscribed");
        }
    }

    @Override
    public void clearSubscriptions() {
        clientSubscription.cleanSubscriptions();
    }
}
