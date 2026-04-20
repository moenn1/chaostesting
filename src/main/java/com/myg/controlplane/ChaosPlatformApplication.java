package com.myg.controlplane;

import com.myg.controlplane.agents.service.AgentRegistryProperties;
import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(AgentRegistryProperties.class)
public class ChaosPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChaosPlatformApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
