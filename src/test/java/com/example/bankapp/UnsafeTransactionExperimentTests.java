package com.example.bankapp;

import com.example.bankapp.exception.BankException;
import com.example.bankapp.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** The deliberately unsafe code exists in test scope only. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3307/bank_assignment_tests?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.username=root", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "bank.seed.enabled=false",
        "bank.transfer.fee-rate=0", "spring.jpa.show-sql=false"
})
@Import(UnsafeTransactionExperimentTests.UnsafeTransferExperiment.class)
class UnsafeTransactionExperimentTests extends BankIntegrationTestSupport {
    @Autowired private UnsafeTransferExperiment unsafe;

    @Test
    void supportsWithoutAnOuterTransactionLosesMoneyThenRepairAndRequiredPreserveIt() throws Exception {
        assertThrows(BankException.class, () -> unsafe.debitThenFail("100001", money("1000")));
        balance("100001", "49000"); balance("100003", "10000"); total("84000");
        evidence("unsafe-experiment.txt", "Task10 UNSAFE observed: SUPPORTS, no outer transaction; debit1000.00 committed by repository; forced failure before credit; Kamal49000.00 Saman10000.00 total84000.00 (lost1000.00).");

        assertEquals(1, customers.credit("100001", money("1000")));
        total("85000");
        evidence("unsafe-experiment.txt", "Task10 REPAIR observed: credited exactly1000.00 to sender in isolated database; total restored85000.00.");

        assertThrows(BankException.class,
                () -> bank.transfer("100001", "100003", money("1000"), true));
        balance("100001", "50000"); balance("100003", "10000"); total("85000");
        assertEquals(0, transactions.count());
        evidence("unsafe-experiment.txt", "Task10 PROTECTED observed: production REQUIRED+rollbackFor transfer with same forced failure; Kamal50000.00 Saman10000.00 total85000.00 unchanged; no SUCCESS row.");
    }

    public static class UnsafeTransferExperiment {
        private final CustomerRepository customers;
        public UnsafeTransferExperiment(CustomerRepository customers) { this.customers = customers; }

        @Transactional(propagation = Propagation.SUPPORTS)
        public void debitThenFail(String sender, BigDecimal amount) {
            if (customers.debit(sender, amount) != 1) {
                throw new BankException("Insufficient balance for experiment");
            }
            throw new BankException("Forced failure after separately committed withdrawal");
        }
    }
}
