package com.arda.iyzico.project.config;

import com.iyzipay.Options;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class IyzicoConfig {

    @Bean
    public Options iyzipayOptions(IyzicoProperties properties) {
        if (properties.apiKey() == null || properties.apiKey().startsWith("your-")) {
            throw new IllegalStateException(
                    "iyzico.api-key is unset (currently '" + properties.apiKey() + "'). "
                    + "Put real sandbox credentials in config/application.yml.");
        }

        log.info("iyzico configured against {} with api key {}...",
                properties.baseUrl(), properties.apiKey().substring(0, 12));

        Options options = new Options();
        options.setApiKey(properties.apiKey());
        options.setSecretKey(properties.secretKey());
        options.setBaseUrl(properties.baseUrl());
        return options;
    }
}
