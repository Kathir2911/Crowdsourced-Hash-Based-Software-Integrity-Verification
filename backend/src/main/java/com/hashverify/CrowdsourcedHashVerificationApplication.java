package com.hashverify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main application class for the Crowdsourced Hash Verification System
 */
@SpringBootApplication
@EnableScheduling
@EnableTransactionManagement
public class CrowdsourcedHashVerificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrowdsourcedHashVerificationApplication.class, args);
    }
}