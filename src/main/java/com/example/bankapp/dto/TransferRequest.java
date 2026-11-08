package com.example.bankapp.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

/** Values submitted by the transfer form. */
@Getter
@Setter
public class TransferRequest {
    @NotBlank(message = "Select the sender's account.")
    @Pattern(regexp = "[0-9]{6}", message = "Select a valid six-digit account.")
    private String fromAccount;

    @NotBlank(message = "Select the receiver's account.")
    @Pattern(regexp = "[0-9]{6}", message = "Select a valid six-digit account.")
    private String toAccount;

    @NotNull(message = "Enter the transfer amount.")
    @DecimalMin(value = "0.01", message = "The amount must be at least LKR 0.01.")
    @DecimalMax(value = "999999999999.99", message = "The amount exceeds the supported limit.")
    @Digits(integer = 12, fraction = 2, message = "Use at most 12 digits and two decimal places.")
    private BigDecimal amount;

    private boolean simulateFailure;
}
