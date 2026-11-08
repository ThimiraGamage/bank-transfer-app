package com.example.bankapp;

import com.example.bankapp.model.Customer;
import com.example.bankapp.repository.BankTransactionRepository;
import com.example.bankapp.repository.CustomerRepository;
import com.example.bankapp.service.BankService;
import com.example.bankapp.service.TransactionAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Each test uses committed data and real service transaction boundaries. */
abstract class BankIntegrationTestSupport {
    @Autowired protected BankService bank;
    @Autowired protected TransactionAuditService audit;
    @Autowired protected CustomerRepository customers;
    @Autowired protected BankTransactionRepository transactions;
    @Autowired protected DataSource dataSource;

    @BeforeEach
    void resetOnlyTheIsolatedAssignmentDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("bank_assignment_tests", connection.getCatalog(),
                    "Tests must never run against the normal bank_db database");
        }
        transactions.deleteAllInBatch();
        customers.deleteAllInBatch();
        customers.saveAllAndFlush(List.of(
                new Customer("Kamal Silva", "kamal@example.com", "100001", money("50000")),
                new Customer("Nimali Perera", "nimali@example.com", "100002", money("25000")),
                new Customer("Saman Fernando", "saman@example.com", "100003", money("10000")),
                new Customer("Bank Fees", "bank@example.com", "999999", money("0"))));
    }

    protected static BigDecimal money(String value) { return new BigDecimal(value).setScale(2); }

    protected void balance(String account, String expected) {
        assertEquals(0, money(expected).compareTo(bank.getCustomer(account).getBalance()),
                "Balance of account " + account);
    }

    protected void total(String expected) {
        assertEquals(0, money(expected).compareTo(bank.getTotalBalance()), "Sum of all balances");
    }

    protected void evidence(String file, String content) throws Exception {
        Path directory = Path.of("assignment-work", "evidence");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(file), content + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
