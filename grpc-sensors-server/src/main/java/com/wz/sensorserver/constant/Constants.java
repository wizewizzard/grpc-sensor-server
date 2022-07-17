package com.wz.sensorserver.constant;

import io.grpc.Context;

public class Constants{
        public static final Context.Key<String> SENSOR_ID_CONTEXT_KEY = Context.key("sensorId");
        public static final Context.Key<String> CLIENT_LOGIN_CONTEXT_KEY = Context.key("login");

        private Constants() {
            throw new AssertionError();
        }
}
