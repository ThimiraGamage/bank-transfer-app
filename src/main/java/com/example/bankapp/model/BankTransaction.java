package com.example.bankapp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bank_transactions", indexes = {
        @Index(name = "idx_transaction_from", columnList = "from_account"),
        @Index(name = "idx_transaction_to", columnList = "to_account")
})
@Getter
@Setter
@NoArgsConstructor
public class BankTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Strings preserve failed requests too, including unknown account numbers.
    @Column(name = "from_account", length = 6)
    private String fromAccount;

    @Column(name = "to_account", length = 6)
    private String toAccount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal fee = new BigDecimal("0.00");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type = TransactionType.TRANSFER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionStatus status;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void setCreationTime() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
