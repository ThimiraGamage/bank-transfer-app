package com.example.bankapp.repository;

import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {
    @Query("SELECT t FROM BankTransaction t ORDER BY t.createdAt DESC, t.id DESC")
    List<BankTransaction> findAllLatest();

    @Query("SELECT t FROM BankTransaction t WHERE t.fromAccount = :account OR t.toAccount = :account OR (:account = '999999' AND t.type = com.example.bankapp.model.TransactionType.TRANSFER AND t.status = com.example.bankapp.model.TransactionStatus.SUCCESS AND t.fee > 0) ORDER BY t.createdAt DESC, t.id DESC")
    List<BankTransaction> findHistory(@Param("account") String account);

    @Query("SELECT t FROM BankTransaction t WHERE t.status = :status ORDER BY t.createdAt DESC, t.id DESC")
    List<BankTransaction> findByStatusLatest(@Param("status") TransactionStatus status);

    @Query("SELECT COUNT(t) FROM BankTransaction t WHERE t.status = :status")
    long countByStatus(@Param("status") TransactionStatus status);

    @Query("SELECT COUNT(t) FROM BankTransaction t WHERE t.status = :status AND t.type = com.example.bankapp.model.TransactionType.TRANSFER")
    long countTransfersByStatus(@Param("status") TransactionStatus status);

    @Query("SELECT t FROM BankTransaction t WHERE t.amount > :min AND t.status = com.example.bankapp.model.TransactionStatus.SUCCESS AND t.type = com.example.bankapp.model.TransactionType.TRANSFER ORDER BY t.createdAt DESC, t.id DESC")
    List<BankTransaction> findTransfersAbove(@Param("min") BigDecimal min);

    @Query("SELECT SUM(t.amount) FROM BankTransaction t WHERE t.fromAccount = :account AND t.status = com.example.bankapp.model.TransactionStatus.SUCCESS AND t.type = com.example.bankapp.model.TransactionType.TRANSFER")
    BigDecimal getTotalSent(@Param("account") String account);

    @Query("SELECT SUM(t.fee) FROM BankTransaction t WHERE t.fromAccount = :account AND t.status = com.example.bankapp.model.TransactionStatus.SUCCESS AND t.type = com.example.bankapp.model.TransactionType.TRANSFER")
    BigDecimal getTotalFees(@Param("account") String account);
}
