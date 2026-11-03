package com.bandup.api.vocabulary.infrastructure.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bandup.vocabulary.ai.*}: the OpenRouter connection for word cards.
 *
 * @param apiKey     {@code BANDUP_OPENROUTER_API_KEY}; blank = AI off (new words get 503, known words still work)
 * @param model      the OpenRouter model id
 * @param baseUrl    the OpenRouter API base
 * @param timeout    one call's time limit
 * @param dailyLimit new AI cards one learner may cause per day
 */
@ConfigurationProperties("bandup.vocabulary.ai")
public record AiProperties(String apiKey, String model, String baseUrl, Duration timeout, int dailyLimit) {

    public AiProperties {
        apiKey = apiKey == null ? "" : apiKey.strip();
        model = model == null || model.isBlank() ? "anthropic/claude-haiku-5.5" : model.strip();
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://openrouter.ai/api/v1" : baseUrl.strip();
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        dailyLimit = dailyLimit <= 0 ? 30 : dailyLimit;
    }

    /** Never prints the key. */
    @Override
    public String toString() {
        return "AiProperties[apiKey=" + (apiKey.isEmpty() ? "(none)" : "(set)") + ", model=" + model + ", baseUrl="
                + baseUrl + ", timeout=" + timeout + ", dailyLimit=" + dailyLimit + "]";
    }
}
