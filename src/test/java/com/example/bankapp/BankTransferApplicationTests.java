package com.example.bankapp;

import com.example.bankapp.dto.MoneyRequest;
import com.example.bankapp.dto.TransferRequest;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.model.TransactionType;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3307/bank_assignment_tests?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.username=root", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "bank.seed.enabled=false",
        "bank.transfer.fee-rate=0.01", "spring.jpa.show-sql=false"
})
class BankTransferApplicationTests extends BankIntegrationTestSupport {
    @Autowired private Validator validator;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void successfulTransferCommitsPrincipalFeeAndExactlyOneRecord() throws Exception {
        BankTransaction result = bank.transfer("100001", "100002", money("5000"), false);
        balance("100001", "44950"); balance("100002", "30000"); balance("999999", "50"); total("85000");
        assertEquals(TransactionStatus.SUCCESS, result.getStatus());
        assertEquals(TransactionType.TRANSFER, result.getType());
        assertEquals(0, money("50").compareTo(result.getFee()));
        assertEquals(1, transactions.count());
        evidence("extension-tests.txt", "PASS successful5000.00 transfer with1% fee: Kamal44950.00 Nimali30000.00 bank50.00 total85000.00; one SUCCESS row.");
    }

    @Test
    void simulatedFailureRollsBackTheAlreadyExecutedDebitAndLeavesNoSuccessRecord() throws Exception {
        assertThrows(BankException.class,
                () -> bank.transfer("100001", "100003", money("1000"), true));
        balance("100001", "50000"); balance("100003", "10000"); balance("999999", "0"); total("85000");
        assertEquals(0, transactions.count());
        evidence("extension-tests.txt", "PASS simulated failure after debit: principal and fee debit both rolled back; all balances unchanged; no SUCCESS row.");
    }

    @Test
    void insufficientBalanceIncludesTheFee() {
        assertThrows(BankException.class,
                () -> bank.transfer("100003", "100001", money("10000"), false));
        balance("100003", "10000"); total("85000");
        assertEquals(0, transactions.count());
    }

    @Test
    void receiverOrBankCreditFailureRollsBackAllEarlierAccountUpdates() throws Exception {
        Customer receiver = bank.getCustomer("100002");
        receiver.setBalance(money("999999999999.99"));
        customers.saveAndFlush(receiver);
        assertThrows(BankException.class,
                () -> bank.transfer("100001", "100002", money("1000"), false));
        balance("100001", "50000"); balance("100002", "999999999999.99"); balance("999999", "0");
        assertEquals(0, transactions.count());

        receiver = bank.getCustomer("100002");
        receiver.setBalance(money("25000"));
        customers.saveAndFlush(receiver);
        Customer feeAccount = bank.getCustomer("999999");
        feeAccount.setBalance(money("999999999999.99"));
        customers.saveAndFlush(feeAccount);
        assertThrows(BankException.class,
                () -> bank.transfer("100001", "100002", money("1000"), false));
        balance("100001", "50000"); balance("100002", "25000"); balance("999999", "999999999999.99");
        assertEquals(0, transactions.count());
        evidence("extension-tests.txt", "PASS late credit failures: receiver overflow reverses sender debit; bank fee-account overflow reverses both sender debit and receiver credit; no SUCCESS row.");
    }

    @Test
    void sameMissingAndInternalAccountsCannotTransfer() {
        assertThrows(BankException.class,
                () -> bank.transfer("100001", "100001", money("100"), false));
        assertThrows(BankException.class,
                () -> bank.transfer("100001", "123456", money("100"), false));
        assertThrows(BankException.class,
                () -> bank.transfer("100001", "999999", money("100"), false));
        total("85000"); assertEquals(0, transactions.count());
    }

    @Test
    void invalidMoneyIsRejectedEvenWhenServiceIsCalledWithoutAForm() {
        for (BigDecimal invalid : List.of(new BigDecimal("0"), new BigDecimal("-1"),
                new BigDecimal("0.001"), new BigDecimal("1000000000000"))) {
            assertThrows(BankException.class,
                    () -> bank.transfer("100001", "100002", invalid, false));
        }
        assertThrows(BankException.class,
                () -> bank.transfer("100001", "100002", null, false));
        total("85000");
    }

    @Test
    void failedAuditIsCommittedAfterTheTransferRollback() throws Exception {
        BankException failure = assertThrows(BankException.class,
                () -> bank.transfer("100001", "100003", money("1000"), true));
        audit.recordFailure("100001", "100003", money("1000"), failure.getMessage());
        total("85000"); assertEquals(1, transactions.count());
        assertEquals(TransactionStatus.FAILED, transactions.findAllLatest().getFirst().getStatus());
        assertEquals(0, bank.countByStatus(TransactionStatus.SUCCESS));
        assertEquals(1, bank.countByStatus(TransactionStatus.FAILED));
        evidence("extension-tests.txt", "PASS durable audit: failed transfer rolled back; separate REQUIRES_NEW transaction committed one FAILED row.");
    }

    @Test
    void requiresNewAuditSurvivesRollbackOfACallerTransaction() {
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        assertThrows(BankException.class, () -> outer.executeWithoutResult(status -> {
            audit.recordFailure("100001", "100002", money("100"), "Independent audit test");
            throw new BankException("Roll back the outer transaction");
        }));
        assertEquals(1, transactions.count());
        assertEquals(TransactionStatus.FAILED, transactions.findAllLatest().getFirst().getStatus());
        total("85000");
    }

    @Test
    void depositAndWithdrawalCommitRecordsWithoutBeingCountedAsTransfers() throws Exception {
        bank.deposit("100001", money("500")); bank.withdraw("100001", money("200"));
        balance("100001", "50300"); total("85300");
        assertEquals(2, transactions.count()); assertEquals(0, bank.countByStatus(TransactionStatus.SUCCESS));
        assertEquals(0, bank.getTotalSent("100001").compareTo(BigDecimal.ZERO));
        assertEquals(0, bank.getLargeTransfers(money("100")).size());
        assertTrue(bank.getHistory("100001").stream().anyMatch(t -> t.getType() == TransactionType.DEPOSIT));
        assertTrue(bank.getHistory("100001").stream().anyMatch(t -> t.getType() == TransactionType.WITHDRAWAL));
        assertThrows(BankException.class, () -> bank.withdraw("100003", money("10001")));
        balance("100003", "10000");
        assertThrows(BankException.class, () -> bank.deposit("999999", money("1")));
        evidence("extension-tests.txt", "PASS deposit/withdraw: +500/-200 => Kamal50300.00 total85300.00; two cash-operation records; transfer count and sent-total remain zero.");
    }

    @Test
    void customerQueriesFindSearchOrderCountAndRejectDuplicates() {
        assertEquals("Kamal Silva", customers.getByAccountNumber("100001").orElseThrow().getFullName());
        assertEquals(1, customers.countAccounts("100001")); assertEquals(1, customers.countEmails("kamal@example.com"));
        assertEquals(4, customers.countCustomersNative());
        assertEquals(List.of("Bank Fees", "Kamal Silva", "Nimali Perera", "Saman Fernando"),
                customers.findAllOrdered().stream().map(Customer::getFullName).toList());
        assertEquals(List.of("100001"), customers.searchByName("SiL").stream().map(Customer::getAccountNumber).toList());
        bank.createCustomer(new Customer("Nuwan Jayasinghe", "nuwan@example.com", "100004", money("15000")));
        assertThrows(BankException.class, () -> bank.createCustomer(
                new Customer("Different Name", "different@example.com", "100004", money("0"))));
        assertThrows(BankException.class, () -> bank.createCustomer(
                new Customer("Different Name", "nuwan@example.com", "100005", money("0"))));
        assertEquals(5, customers.countCustomersNative()); total("100000");
    }

    @Test
    void transactionQueriesFilterStatusesHistoryThresholdAndSentTotals() throws Exception {
        bank.transfer("100001", "100002", money("10000"), false);
        bank.transfer("100001", "100003", money("10001"), false);
        audit.recordFailure("100001", "100002", money("20000"), "Rejected test transfer");
        bank.deposit("100002", money("30000"));
        assertEquals(4, transactions.findAllLatest().size());
        assertEquals(3, transactions.findByStatusLatest(TransactionStatus.SUCCESS).size());
        assertEquals(1, transactions.findByStatusLatest(TransactionStatus.FAILED).size());
        assertEquals(3, transactions.countByStatus(TransactionStatus.SUCCESS));
        assertEquals(2, transactions.countTransfersByStatus(TransactionStatus.SUCCESS));
        assertEquals(3, transactions.findHistory("100001").size());
        assertEquals(3, transactions.findHistory("100002").size());
        assertEquals(1, transactions.findTransfersAbove(money("10000")).size());
        assertEquals(0, money("10001").compareTo(transactions.findTransfersAbove(money("10000")).getFirst().getAmount()));
        assertEquals(0, money("20001").compareTo(bank.getTotalSent("100001")));
        assertEquals(0, money("200.01").compareTo(bank.getTotalFees("100001")));
        assertEquals(0, BigDecimal.ZERO.compareTo(bank.getTotalSent("100003")));
        evidence("extension-tests.txt", "PASS @Query reports: strict>10000 returns only SUCCESS10001.00 transfer; FAILED20000 and DEPOSIT30000 excluded. Kamal principal sent20001.00 fees200.01.");
    }

    @Test
    void beanValidationRejectsEmptyZeroAndOverPreciseAmounts() {
        TransferRequest request = new TransferRequest();
        request.setFromAccount("100001"); request.setToAccount("100002");
        assertFalse(validator.validate(request).isEmpty());
        request.setAmount(money("0")); assertFalse(validator.validate(request).isEmpty());
        request.setAmount(new BigDecimal("1.001")); assertFalse(validator.validate(request).isEmpty());
        request.setAmount(money("1")); assertTrue(validator.validate(request).isEmpty());
        MoneyRequest cash = new MoneyRequest(); assertFalse(validator.validate(cash).isEmpty());
        cash.setAccountNumber("100001"); cash.setAmount(money("1")); assertTrue(validator.validate(cash).isEmpty());
    }

    @Test
    void conditionalDebitPreventsTwoConcurrentTransfersFromOverspending() throws Exception {
        customers.debit("100001", money("49000"));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> concurrentTransfer(start, "100002"));
            Future<Boolean> second = executor.submit(() -> concurrentTransfer(start, "100003"));
            start.countDown();
            int successful = (first.get(15, TimeUnit.SECONDS) ? 1 : 0) + (second.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successful);
        }
        balance("100001", "394"); balance("999999", "6"); total("36000");
        assertEquals(1, transactions.count());
        evidence("extension-tests.txt", "PASS concurrent debit: two attempted600.00 transfers from1000.00 balance; exactly one committed606.00 debit; sender394.00 bank6.00; no overdraft; total conserved.");
    }

    private boolean concurrentTransfer(CountDownLatch start, String receiver) throws Exception {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try { bank.transfer("100001", receiver, money("600"), false); return true; }
        catch (BankException expected) { return false; }
    }
}
