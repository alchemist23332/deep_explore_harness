package com.alchemist.deepexplore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DeepExploreApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeepExploreApplication.class, args);
    }
}
