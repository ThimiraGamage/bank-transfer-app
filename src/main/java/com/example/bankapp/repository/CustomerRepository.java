package com.example.bankapp.repository;

import com.example.bankapp.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @Query("SELECT c FROM Customer c WHERE c.accountNumber = :accountNumber")
    Optional<Customer> getByAccountNumber(@Param("accountNumber") String accountNumber);

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.accountNumber = :accountNumber")
    long countAccounts(@Param("accountNumber") String accountNumber);

    @Query("SELECT COUNT(c) FROM Customer c WHERE LOWER(c.email) = LOWER(:email)")
    long countEmails(@Param("email") String email);

    @Query("SELECT c FROM Customer c ORDER BY c.fullName, c.accountNumber")
    List<Customer> findAllOrdered();

    @Query("SELECT c FROM Customer c WHERE LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY c.fullName, c.accountNumber")
    List<Customer> searchByName(@Param("keyword") String keyword);

    @Query("SELECT SUM(c.balance) FROM Customer c")
    BigDecimal getTotalBalance();

    @Query(value = "SELECT COUNT(*) FROM customers", nativeQuery = true)
    long countCustomersNative();

    // One conditional statement prevents concurrent requests from overdrawing an account.
    // REQUIRED joins the outer transfer or starts a transaction for a standalone call.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Customer c SET c.balance = c.balance - :amount WHERE c.accountNumber = :accountNumber AND c.balance >= :amount AND :amount > 0")
    int debit(@Param("accountNumber") String accountNumber, @Param("amount") BigDecimal amount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Customer c SET c.balance = c.balance + :amount WHERE c.accountNumber = :accountNumber AND :amount > 0 AND c.balance <= 999999999999.99 - :amount")
    int credit(@Param("accountNumber") String accountNumber, @Param("amount") BigDecimal amount);
}
