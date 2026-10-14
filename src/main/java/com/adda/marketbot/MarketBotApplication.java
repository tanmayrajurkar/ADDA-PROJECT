package com.adda.marketbot;

import com.adda.marketbot.retrieval.TfidfIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MarketBotApplication {

    private static final Logger log = LoggerFactory.getLogger(MarketBotApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(MarketBotApplication.class, args);
    }

    @Bean
    CommandLineRunner logKnowledgeBase(TfidfIndex index) {
        return args -> log.info("Indian stock market knowledge base ready with {} passages", index.size());
    }
}
