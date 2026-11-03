package com.bandup.api.vocabulary.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bandup.api.vocabulary.application.port.VocabularySettings;
import com.bandup.api.vocabulary.infrastructure.ai.AiProperties;

/** Vocabulary settings ({@code bandup.vocabulary.*}). */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
class VocabularyConfig {

    @Bean
    VocabularySettings vocabularySettings(AiProperties ai) {
        return ai::dailyLimit;
    }
}
