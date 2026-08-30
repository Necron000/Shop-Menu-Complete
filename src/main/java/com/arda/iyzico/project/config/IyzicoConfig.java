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

        // The callback URL is baked into every payment at initialize time and is
        // opened by the BUYER's browser, not by our server -- so a localhost value
        // works only for someone sitting at this machine. A remote buyer pays, gets
        // sent to localhost:8080, and sees a connection error while the order sits
        // at AWAITING_PAYMENT forever. Print it on every boot, and shout when it
        // cannot possibly work for anyone else.
        log.info("iyzico callback-url        {}", properties.callbackUrl());
        log.info("iyzico frontend-result-url {}", properties.frontendResultUrl());

        if (isLocal(properties.callbackUrl())) {
            log.warn("""
                    iyzico.callback-url points at {} -- only buyers ON THIS MACHINE can \
                    complete a payment. Anyone else will be sent to their own localhost \
                    and get a connection error. Set iyzico.callback-url and \
                    iyzico.frontend-result-url in config/application.yml (or PUBLIC_BASE_URL) \
                    to a hostname the buyer's browser can reach.""",
                    properties.callbackUrl());
        }

        Options options = new Options();
        options.setApiKey(properties.apiKey());
        options.setSecretKey(properties.secretKey());
        options.setBaseUrl(properties.baseUrl());
        return options;
    }

    private static boolean isLocal(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.contains("//localhost") || lower.contains("//127.0.0.1");
    }
}
