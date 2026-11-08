package com.example.bankapp.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

/** Server-validated input for a cash deposit or withdrawal. */
@Getter
@Setter
public class MoneyRequest {
    @NotBlank(message = "Select a customer account.")
    @Pattern(regexp = "[0-9]{6}", message = "Select a valid six-digit account.")
    private String accountNumber;

    @NotNull(message = "Enter an amount.")
    @DecimalMin(value = "0.01", message = "The amount must be at least LKR 0.01.")
    @DecimalMax(value = "999999999999.99", message = "The amount exceeds the supported limit.")
    @Digits(integer = 12, fraction = 2, message = "Use at most 12 digits and two decimal places.")
    private BigDecimal amount;
}
