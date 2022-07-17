package com.wz.sensorserver.mq;

import com.wz.sensorserver.exception.SubscriptionException;

import java.util.function.Consumer;

public interface MQSubscriptionManager <K, T> {
    void trySubscribe(String key, Consumer<T> messageConsumer) throws SubscriptionException;

    void tryUnsubscribe(K key) throws SubscriptionException;

    void clearSubscriptions();
}
