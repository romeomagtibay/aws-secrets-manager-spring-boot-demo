package com.example.springbootdemo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.net.URI;

@SpringBootApplication
public class SpringBootDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootDemoApplication.class, args);
    }

    @Bean
    @Profile("local")
    ApplicationRunner readSecret(@Value("${AWS_ENDPOINT_URL}") String endpointUrl) {
        return args -> {
            URI endpoint = URI.create(endpointUrl);
            if (!"https".equals(endpoint.getScheme()) || !"127.0.0.1".equals(endpoint.getHost())) {
                throw new IllegalArgumentException("Use the local HTTPS emulator endpoint");
            }

            try (var credentials = ProfileCredentialsProvider.create("default");
                 var client = SecretsManagerClient.builder()
                         .endpointOverride(endpoint)
                         .credentialsProvider(credentials)
                         .build()) {
                String token = client.getSecretValue(
                        request -> request.secretId("demo/springboot")).secretString();

                if (token == null || token.isBlank()) {
                    throw new IllegalStateException("The demo secret is empty");
                }

                System.out.println("token : " + token);
                System.out.println("Read secret demo/springboot successfully.");
            }
        };
    }
}
