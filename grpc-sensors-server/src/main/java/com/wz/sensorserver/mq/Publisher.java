package com.wz.sensorserver.mq;

public interface Publisher<T> {
    void publishMessage(T message);

    void cleanUp();
}
