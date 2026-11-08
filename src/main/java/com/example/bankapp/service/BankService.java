package com.example.bankapp.service;

import com.example.bankapp.exception.AccountNotFoundException;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.exception.InsufficientBalanceException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.model.TransactionType;
import com.example.bankapp.repository.BankTransactionRepository;
import com.example.bankapp.repository.CustomerRepository;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankService {
    public static final String BANK_ACCOUNT = "999999";
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal MAX_MONEY = new BigDecimal("999999999999.99");

    private final CustomerRepository customers;
    private final BankTransactionRepository transactions;

    @Value("${bank.transfer.fee-rate:0.01}")
    private BigDecimal feeRate;

    @PostConstruct
    void validateFeeRate() {
        if (feeRate.signum() < 0 || feeRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("bank.transfer.fee-rate must be between 0 and 1.");
        }
    }

    /** One transaction covers the sender, receiver, bank fee and success record. */
    @Transactional(rollbackFor = Exception.class)
    public BankTransaction transfer(String fromAccount, String toAccount, BigDecimal amount,
                                    boolean simulateFailure) {
        BigDecimal principal = requireAmount(amount);
        requireCustomerAccount(fromAccount);
        requireCustomerAccount(toAccount);
        if (fromAccount.equals(toAccount)) {
            throw new BankException("Sender and receiver must be different accounts.");
        }
        getCustomer(fromAccount);
        getCustomer(toAccount);
        getCustomer(BANK_ACCOUNT);

        BigDecimal fee = principal.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalDebit = principal.add(fee);
        if (customers.debit(fromAccount, totalDebit) != 1) {
            throw new InsufficientBalanceException(fromAccount);
        }
        if (simulateFailure) {
            // The conditional UPDATE has executed, so this demonstrates a real rollback.
            throw new BankException("Simulated system failure after withdrawal.");
        }
        creditOrThrow(toAccount, principal);
        if (fee.signum() > 0) {
            creditOrThrow(BANK_ACCOUNT, fee);
        }
        return saveSuccess(TransactionType.TRANSFER, fromAccount, toAccount, principal, fee,
                "Transfer completed; fee LKR " + fee.toPlainString() + ".");
    }

    @Transactional
    public Customer createCustomer(@Valid Customer customer) {
        if (customer.getId() != null) {
            throw new BankException("Use the customer form to create a new account.");
        }
        requireCustomerAccount(customer.getAccountNumber());
        if (customers.countAccounts(customer.getAccountNumber()) > 0) {
            throw new BankException("Account " + customer.getAccountNumber() + " already exists.");
        }
        String email = customer.getEmail().trim().toLowerCase(Locale.ROOT);
        if (customers.countEmails(email) > 0) {
            throw new BankException("That email address already exists.");
        }
        customer.setFullName(customer.getFullName().trim());
        customer.setEmail(email);
        customer.setBalance(requireBalance(customer.getBalance()));
        // Database UNIQUE constraints are the final protection for concurrent submissions.
        return customers.saveAndFlush(customer);
    }

    @Transactional(rollbackFor = Exception.class)
    public BankTransaction deposit(String accountNumber, BigDecimal amount) {
        BigDecimal value = requireAmount(amount);
        requireCustomerAccount(accountNumber);
        getCustomer(accountNumber);
        creditOrThrow(accountNumber, value);
        return saveSuccess(TransactionType.DEPOSIT, null, accountNumber, value, ZERO,
                "Cash deposit completed.");
    }

    @Transactional(rollbackFor = Exception.class)
    public BankTransaction withdraw(String accountNumber, BigDecimal amount) {
        BigDecimal value = requireAmount(amount);
        requireCustomerAccount(accountNumber);
        getCustomer(accountNumber);
        if (customers.debit(accountNumber, value) != 1) {
            throw new InsufficientBalanceException(accountNumber);
        }
        return saveSuccess(TransactionType.WITHDRAWAL, accountNumber, null, value, ZERO,
                "Cash withdrawal completed.");
    }

    public List<Customer> getAllCustomers() {
        return customers.findAllOrdered();
    }

    public List<Customer> searchCustomers(String keyword) {
        return keyword == null || keyword.isBlank()
                ? customers.findAllOrdered() : customers.searchByName(keyword.trim());
    }

    public Customer getCustomer(String accountNumber) {
        return customers.getByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    /** Includes the bank fee account: transfers preserve this total. */
    public BigDecimal getTotalBalance() {
        return zeroIfNull(customers.getTotalBalance());
    }

    /** The internal fee account is an account, but is not a customer. */
    public long countCustomers() {
        return customers.countCustomersNative() - customers.countAccounts(BANK_ACCOUNT);
    }

    public long countAllAccounts() {
        return customers.countCustomersNative();
    }

    public List<BankTransaction> getTransactions() {
        return transactions.findAllLatest();
    }

    public List<BankTransaction> getHistory(String accountNumber) {
        getCustomer(accountNumber);
        return transactions.findHistory(accountNumber);
    }

    /** Dashboard cards count transfers, excluding cash deposits and withdrawals. */
    public long countByStatus(TransactionStatus status) {
        return transactions.countTransfersByStatus(status);
    }

    public List<BankTransaction> getLargeTransfers(BigDecimal minimum) {
        BigDecimal threshold = requireBalance(minimum);
        return transactions.findTransfersAbove(threshold);
    }

    /** Principal only; charged fees are reported separately. */
    public BigDecimal getTotalSent(String accountNumber) {
        return zeroIfNull(transactions.getTotalSent(accountNumber));
    }

    public BigDecimal getTotalFees(String accountNumber) {
        return zeroIfNull(transactions.getTotalFees(accountNumber));
    }

    public BigDecimal getFeeRate() {
        return feeRate;
    }

    private BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BankException("The amount must be at least LKR 0.01.");
        }
        return requireBalance(amount);
    }

    private BigDecimal requireBalance(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(MAX_MONEY) > 0
                || value.stripTrailingZeros().scale() > 2) {
            throw new BankException("Enter a valid amount with at most 12 digits and two decimal places.");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private void requireCustomerAccount(String accountNumber) {
        if (accountNumber == null || !accountNumber.matches("[0-9]{6}")) {
            throw new BankException("The account number must contain exactly six digits.");
        }
        if (BANK_ACCOUNT.equals(accountNumber)) {
            throw new BankException("Account 999999 is reserved for bank fees.");
        }
    }

    private void creditOrThrow(String accountNumber, BigDecimal amount) {
        if (customers.credit(accountNumber, amount) != 1) {
            throw new BankException("Account " + accountNumber
                    + " could not be credited. Check that it exists and its balance is within the supported limit.");
        }
    }

    private BankTransaction saveSuccess(TransactionType type, String from, String to,
                                        BigDecimal amount, BigDecimal fee, String description) {
        BankTransaction transaction = new BankTransaction();
        transaction.setType(type);
        transaction.setFromAccount(from);
        transaction.setToAccount(to);
        transaction.setAmount(amount);
        transaction.setFee(fee);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setDescription(description);
        return transactions.saveAndFlush(transaction);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.UNNECESSARY);
    }
}
