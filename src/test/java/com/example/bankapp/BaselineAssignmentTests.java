package com.example.bankapp;

import com.example.bankapp.exception.BankException;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/** Task 9 is run with the original zero-fee rule, before the Task 12 extension. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3307/bank_assignment_tests?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.username=root", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "bank.seed.enabled=false",
        "bank.transfer.fee-rate=0", "spring.jpa.show-sql=false"
})
class BaselineAssignmentTests extends BankIntegrationTestSupport {
    @Test
    void baselineTask9SequenceMatchesTheAssignmentBalances() throws Exception {
        bank.transfer("100001", "100002", money("5000"), false);
        balance("100001", "45000"); balance("100002", "30000"); total("85000");
        evidence("baseline-tests.txt", "Task9/1 PASS: 5000.00 transfer; Kamal45000.00 Nimali30000.00 Saman10000.00 total85000.00, one SUCCESS.");

        BankException insufficient = assertThrows(BankException.class,
                () -> bank.transfer("100003", "100001", money("20000"), false));
        audit.recordFailure("100003", "100001", money("20000"), insufficient.getMessage());
        total("85000"); balance("100003", "10000");
        evidence("baseline-tests.txt", "Task9/2 PASS: insufficient20000.00 from Saman rejected; balances unchanged; FAILED audit committed.");

        BankException same = assertThrows(BankException.class,
                () -> bank.transfer("100001", "100001", money("1000"), false));
        audit.recordFailure("100001", "100001", money("1000"), same.getMessage());
        total("85000");
        evidence("baseline-tests.txt", "Task9/3 PASS: same account rejected; balances unchanged; FAILED audit committed.");

        BankException crash = assertThrows(BankException.class,
                () -> bank.transfer("100001", "100003", money("1000"), true));
        audit.recordFailure("100001", "100003", money("1000"), crash.getMessage());
        balance("100001", "45000"); balance("100003", "10000"); total("85000");
        assertEquals(1, bank.countByStatus(TransactionStatus.SUCCESS));
        assertEquals(3, bank.countByStatus(TransactionStatus.FAILED));
        evidence("baseline-tests.txt", "Task9/4 PASS: forced failure after1000.00 debit; Kamal45000.00 Saman10000.00 total85000.00; 1SUCCESS/3FAILED.");

        bank.createCustomer(new Customer("Nuwan Jayasinghe", "nuwan@example.com", "100004", money("15000")));
        assertThrows(BankException.class, () -> bank.createCustomer(
                new Customer("Nuwan Jayasinghe", "nuwan2@example.com", "100004", money("15000"))));
        total("100000");
        assertEquals(1, bank.searchCustomers("sil").size());
        assertEquals("100001", bank.searchCustomers("sil").getFirst().getAccountNumber());
        assertEquals(4, bank.getHistory("100001").size());
        evidence("baseline-tests.txt", "Task9/6-7 PASS: Nuwan added15000.00; duplicate account rejected; total100000.00; search sil returns only Kamal; his history contains 1SUCCESS/3FAILED.");
    }
}
