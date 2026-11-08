package com.example.bankapp.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Enter the customer's full name.")
    @Size(max = 100, message = "The name must contain at most 100 characters.")
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @NotBlank(message = "Enter an email address.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 150, message = "The email address is too long.")
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @NotBlank(message = "Enter a six-digit account number.")
    @Pattern(regexp = "[0-9]{6}", message = "The account number must contain exactly six digits.")
    @Column(name = "account_number", nullable = false, unique = true, length = 6)
    private String accountNumber;

    @NotNull(message = "Enter an opening balance.")
    @DecimalMin(value = "0.00", message = "The balance cannot be negative.")
    @DecimalMax(value = "999999999999.99", message = "The balance exceeds the supported limit.")
    @Digits(integer = 12, fraction = 2, message = "Use at most 12 digits and two decimal places.")
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balance;

    public Customer(String fullName, String email, String accountNumber, BigDecimal balance) {
        this.fullName = fullName;
        this.email = email;
        this.accountNumber = accountNumber;
        this.balance = balance;
    }
}
