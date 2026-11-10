package com.skillrental;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SkillRentalApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkillRentalApplication.class,args);
    }
}
