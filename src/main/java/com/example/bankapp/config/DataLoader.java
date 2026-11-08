package com.example.bankapp.config;

import com.example.bankapp.model.Customer;
import com.example.bankapp.repository.CustomerRepository;
import com.example.bankapp.service.BankService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

/** Seeds the lab accounts once; restarting never resets existing balances. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bank.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataLoader implements CommandLineRunner {
    private final CustomerRepository customers;

    @Override
    @Transactional
    public void run(String... args) {
        if (customers.count() == 0) {
            customers.saveAll(List.of(
                    new Customer("Kamal Silva", "kamal@example.com", "100001", new BigDecimal("50000.00")),
                    new Customer("Nimali Perera", "nimali@example.com", "100002", new BigDecimal("25000.00")),
                    new Customer("Saman Fernando", "saman@example.com", "100003", new BigDecimal("10000.00"))
            ));
        }
        if (customers.countAccounts(BankService.BANK_ACCOUNT) == 0) {
            customers.save(new Customer("Bank Fee Account", "bank-fees@example.com", BankService.BANK_ACCOUNT,
                    new BigDecimal("0.00")));
        }
    }
}
