package com.wz.sensorserver.util;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class ThreadCountReporter implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ThreadCountReporter.class);

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(1000);
                logger.info("Thread count: " + Thread.activeCount());
            } catch (InterruptedException ex) {
                return;
            }
        }
    }
}
