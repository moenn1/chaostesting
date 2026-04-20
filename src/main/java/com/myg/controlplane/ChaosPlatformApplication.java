package com.myg.controlplane;

import com.myg.controlplane.agents.service.AgentRegistryProperties;
import com.myg.controlplane.safety.SafetyGuardrailsProperties;
import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootApplication
@EnableConfigurationProperties({AgentRegistryProperties.class, SafetyGuardrailsProperties.class})
public class ChaosPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChaosPlatformApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("chaos-run-");
        scheduler.initialize();
        return scheduler;
    }
}
