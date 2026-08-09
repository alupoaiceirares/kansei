package org.kansei.wirehood;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WirehoodApplication {

    static void main(String[] args) {
        SpringApplication.run(WirehoodApplication.class, args);
    }
}
