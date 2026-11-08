package com.example.bankapp.service;

import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.model.TransactionType;
import com.example.bankapp.repository.BankTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

/** Called by the controller after the failed transfer transaction has rolled back. */
@Service
@RequiredArgsConstructor
public class TransactionAuditService {
    private final BankTransactionRepository transactions;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BankTransaction recordFailure(String fromAccount, String toAccount,
                                         BigDecimal amount, String reason) {
        return saveFailure(TransactionType.TRANSFER, fromAccount, toAccount, amount, reason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BankTransaction recordFailure(TransactionType type, String fromAccount,
                                         String toAccount, BigDecimal amount, String reason) {
        return saveFailure(type, fromAccount, toAccount, amount, reason);
    }

    private BankTransaction saveFailure(TransactionType type, String fromAccount,
                                        String toAccount, BigDecimal amount, String reason) {
        BankTransaction transaction = new BankTransaction();
        transaction.setType(type);
        transaction.setFromAccount(fromAccount);
        transaction.setToAccount(toAccount);
        transaction.setAmount(amount);
        transaction.setFee(new BigDecimal("0.00"));
        transaction.setStatus(TransactionStatus.FAILED);
        String message = reason == null || reason.isBlank() ? "The transaction failed." : reason;
        transaction.setDescription(message.substring(0, Math.min(message.length(), 500)));
        return transactions.saveAndFlush(transaction);
    }
}
