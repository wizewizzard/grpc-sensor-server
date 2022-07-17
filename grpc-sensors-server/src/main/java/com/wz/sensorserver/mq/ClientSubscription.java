package com.wz.sensorserver.mq;

import com.wz.sensorserver.exception.InvalidRequestException;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages list of subscriptions a client has
 */
@Slf4j
public class ClientSubscription {
    private final Map<String, MQConsumer<? extends Serializable>> subscribers;
    private final Lock subscriptionsLock;

    public ClientSubscription() {
        subscribers = new HashMap<>();
        subscriptionsLock = new ReentrantLock();
    }

    public boolean isSubscribed(String key) {
        log.trace("Check if client is subscribed on {}", key);
        subscriptionsLock.lock();
        try {
            return subscribers.get(key) != null;
        } finally {
            subscriptionsLock.unlock();
        }
    }

    public void subscribeConsumer(String key, MQConsumer<? extends Serializable> consumer) {
        log.trace("Subscribing consumer on {}", key);
        subscriptionsLock.lock();
        try {
            if (subscribers.get(key) == null)
                subscribers.put(key, consumer);
            else {
                log.info("Already subscribed on {}. Unable to perform this operation", key);
                throw new InvalidRequestException("Already subscribed");
            }

        } finally {
            subscriptionsLock.unlock();
        }
    }

    public void unsubscribeConsumer(String key) {
        log.trace("Unsubscribing consumer from {}", key);
        subscriptionsLock.lock();
        try {
            if (subscribers.get(key) != null) {
                MQConsumer<? extends Serializable> consumer = subscribers.remove(key);
                consumer.cleanUp();
            } else {
                log.info("Not subscribed on {} to perform this operation", key);
                throw new InvalidRequestException("Not subscribed");
            }
        } finally {
            subscriptionsLock.unlock();
        }
    }

    public void cleanSubscriptions() {
        log.trace("Cleaning the subscriptions");
        subscriptionsLock.lock();
        try {
            subscribers.values().forEach(MQConsumer::cleanUp);
            subscribers.clear();
        } finally {
            subscriptionsLock.unlock();
        }
    }
}
