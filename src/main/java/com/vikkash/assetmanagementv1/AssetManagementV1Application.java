package com.vikkash.assetmanagementv1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AssetManagementV1Application {

    public static void main(String[] args) {
        SpringApplication.run(AssetManagementV1Application.class, args);
    }

}
