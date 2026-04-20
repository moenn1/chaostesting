package com.myg.controlplane;

import com.myg.controlplane.agents.runtime.AgentRuntimeProperties;
import com.myg.controlplane.agents.service.AgentRegistryProperties;
import com.myg.controlplane.safety.SafetyGuardrailsProperties;
import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        AgentRegistryProperties.class,
        AgentRuntimeProperties.class,
        SafetyGuardrailsProperties.class
})
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
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("agent-runtime-");
        scheduler.initialize();
        return scheduler;
    }
}
