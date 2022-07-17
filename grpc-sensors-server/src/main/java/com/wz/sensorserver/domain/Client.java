package com.wz.sensorserver.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Client {
    private String login;
    private String email;
    private String password;
}
