package org.kansei.shieldwall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ShieldwallApplication {

    static void main(String[] args) {
        SpringApplication.run(ShieldwallApplication.class, args);
    }
}
