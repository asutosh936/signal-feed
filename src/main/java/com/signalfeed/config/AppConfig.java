package com.signalfeed.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /**
     * Provider-agnostic ChatClient. The concrete implementation (Anthropic,
     * OpenAI, etc.) is determined by whichever Spring AI starter is on the
     * classpath — controlled by the active Maven profile.
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
