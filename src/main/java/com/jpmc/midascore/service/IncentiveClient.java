package com.jpmc.midascore.service;

import com.jpmc.midascore.foundation.Incentive;
import com.jpmc.midascore.foundation.Transaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class IncentiveClient {

    private static final Logger logger = LoggerFactory.getLogger(IncentiveClient.class);

    private final RestTemplate restTemplate;

    public IncentiveClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${incentive.api.base-url:http://localhost:8080}") String baseUrl) {
        this.restTemplate = restTemplateBuilder.rootUri(baseUrl).build();
    }

    public BigDecimal fetchIncentive(Transaction transaction) {
        try {
            Incentive incentive =
                    restTemplate.postForObject("/incentive", transaction, Incentive.class);
            if (incentive == null) {
                return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            BigDecimal amount =
                    new BigDecimal(Float.toString(Math.max(0.0f, incentive.getAmount())));
            return amount.setScale(2, RoundingMode.HALF_UP);
        } catch (RestClientException ex) {
            logger.warn("Failed to retrieve incentive for transaction {}", transaction, ex);
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }
}
