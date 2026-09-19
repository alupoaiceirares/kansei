package org.kansei.tailwind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TailwindApplication {

    static void main(String[] args) {
        SpringApplication.run(TailwindApplication.class, args);
    }
}
