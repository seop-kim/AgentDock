package com.agent.dock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AgentDockApplication {

	public static void main(String[] args) {
		SpringApplication.run(AgentDockApplication.class, args);
	}

}
