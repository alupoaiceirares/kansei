package org.kansei.fdr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FdrApplication {

    static void main(String[] args) {
        SpringApplication.run(FdrApplication.class, args);
    }
}
