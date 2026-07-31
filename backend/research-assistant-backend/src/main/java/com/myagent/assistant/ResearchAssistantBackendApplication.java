package com.myagent.assistant;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.myagent.assistant.**.mapper")
@SpringBootApplication
public class ResearchAssistantBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResearchAssistantBackendApplication.class, args);
    }
}
