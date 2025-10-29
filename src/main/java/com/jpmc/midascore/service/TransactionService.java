package com.jpmc.midascore.service;

import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.repository.TransactionRecordRepository;
import com.jpmc.midascore.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

    private final UserRepository userRepository;
    private final TransactionRecordRepository transactionRecordRepository;
    private final IncentiveClient incentiveClient;

    public TransactionService(
            UserRepository userRepository,
            TransactionRecordRepository transactionRecordRepository,
            IncentiveClient incentiveClient) {
        this.userRepository = userRepository;
        this.transactionRecordRepository = transactionRecordRepository;
        this.incentiveClient = incentiveClient;
    }

    @Transactional
    public boolean process(Transaction transaction) {
        if (transaction == null) {
            return false;
        }

        BigDecimal amount = toMoney(transaction.getAmount());
        if (amount.signum() <= 0) {
            logger.debug("Discarding transaction with non-positive amount {}", transaction);
            return false;
        }

        UserRecord sender = userRepository.findById(transaction.getSenderId());
        UserRecord recipient = userRepository.findById(transaction.getRecipientId());

        if (sender == null || recipient == null) {
            logger.debug("Discarding transaction with invalid participant(s): {}", transaction);
            return false;
        }

        BigDecimal senderBalance = toMoney(sender.getBalance());
        if (senderBalance.compareTo(amount) < 0) {
            logger.debug("Discarding transaction due to insufficient funds: {}", transaction);
            return false;
        }

        BigDecimal incentiveAmount = incentiveClient.fetchIncentive(transaction);

        BigDecimal updatedSenderBalance = senderBalance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal updatedRecipientBalance = toMoney(recipient.getBalance())
                .add(amount)
                .add(incentiveAmount)
                .setScale(2, RoundingMode.HALF_UP);

        sender.setBalance(updatedSenderBalance.floatValue());
        recipient.setBalance(updatedRecipientBalance.floatValue());

        transactionRecordRepository.save(new TransactionRecord(sender, recipient, amount, incentiveAmount));
        userRepository.save(sender);
        userRepository.save(recipient);

        logger.debug("Recorded transaction {}", transaction);
        return true;
    }

    private BigDecimal toMoney(float value) {
        return new BigDecimal(Float.toString(value)).setScale(2, RoundingMode.HALF_UP);
    }
}
