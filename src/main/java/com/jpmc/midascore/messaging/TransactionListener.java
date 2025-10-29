package com.jpmc.midascore.messaging;

import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransactionListener {

    private static final Logger logger = LoggerFactory.getLogger(TransactionListener.class);
    private final TransactionService transactionService;

    public TransactionListener(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @KafkaListener(topics = "${general.kafka-topic}", containerFactory = "transactionKafkaListenerContainerFactory")
    public void listen(Transaction transaction) {
        boolean processed = transactionService.process(transaction);
        if (processed) {
            logger.info("Recorded transaction {}", transaction);
        } else {
            logger.info("Discarded transaction {}", transaction);
        }
    }
}
