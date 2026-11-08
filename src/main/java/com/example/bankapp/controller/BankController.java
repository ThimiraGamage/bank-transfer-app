package com.example.bankapp.controller;

import com.example.bankapp.dto.MoneyRequest;
import com.example.bankapp.dto.TransferRequest;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.model.TransactionType;
import com.example.bankapp.service.BankService;
import com.example.bankapp.service.TransactionAuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class BankController {
    private static final Logger log = LoggerFactory.getLogger(BankController.class);
    private final BankService bankService;
    private final TransactionAuditService auditService;

    @GetMapping("/")
    public String dashboard(@RequestParam(required = false) String keyword, Model model) {
        populateDashboard(model, keyword);
        return "bank";
    }

    @PostMapping("/customers")
    public String createCustomer(@Valid @ModelAttribute("customer") Customer customer,
            BindingResult result, Model model, RedirectAttributes flash) {
        if (!result.hasErrors()) {
            try {
                bankService.createCustomer(customer);
                flash.addFlashAttribute("success", "Customer " + customer.getFullName() + " added.");
                return "redirect:/";
            } catch (BankException ex) {
                result.rejectValue(ex.getMessage().toLowerCase().contains("email")
                        ? "email" : "accountNumber", "duplicate", ex.getMessage());
            } catch (RuntimeException ex) {
                log.error("Could not create customer", ex);
                model.addAttribute("error", "The customer could not be saved. Check for duplicate details.");
            }
        }
        populateDashboard(model, null);
        return "bank";
    }

    @PostMapping("/transfer")
    public String transfer(@Valid @ModelAttribute("transferRequest") TransferRequest request,
            BindingResult result, Model model, RedirectAttributes flash) {
        if (result.hasErrors()) {
            populateDashboard(model, null);
            return "bank";
        }
        try {
            bankService.transfer(request.getFromAccount(), request.getToAccount(),
                    request.getAmount(), request.isSimulateFailure());
            flash.addFlashAttribute("success", "Transfer completed. All balance updates were committed.");
        } catch (RuntimeException ex) {
            // The service proxy has already rolled back before this catch block runs.
            String reason = failureMessage(ex);
            boolean recorded = recordFailure(TransactionType.TRANSFER, request.getFromAccount(),
                    request.getToAccount(), request.getAmount(), reason);
            flash.addFlashAttribute("error", reason + " All changes were rolled back."
                    + (recorded ? "" : " The failure audit could not be saved."));
        }
        return "redirect:/";
    }

    @GetMapping("/accounts/{accountNumber}/history")
    public String history(@PathVariable String accountNumber, Model model, RedirectAttributes flash) {
        try {
            model.addAttribute("customer", bankService.getCustomer(accountNumber));
            model.addAttribute("transactions", bankService.getHistory(accountNumber));
            model.addAttribute("totalSent", bankService.getTotalSent(accountNumber));
            model.addAttribute("totalFees", bankService.getTotalFees(accountNumber));
            return "history";
        } catch (BankException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/large-transfers")
    public String largeTransfers(@RequestParam(defaultValue = "10000") String min, Model model) {
        BigDecimal minimum = new BigDecimal("10000.00");
        try {
            minimum = new BigDecimal(min);
            if (minimum.signum() < 0 || minimum.scale() > 2
                    || minimum.compareTo(new BigDecimal("999999999999.99")) > 0) {
                throw new NumberFormatException();
            }
            model.addAttribute("transfers", bankService.getLargeTransfers(minimum));
        } catch (NumberFormatException ex) {
            model.addAttribute("error", "Enter a non-negative minimum with at most two decimal places.");
            model.addAttribute("transfers", List.of());
            minimum = new BigDecimal("10000.00");
        }
        model.addAttribute("min", minimum);
        return "large-transfers";
    }

    @GetMapping({"/deposit", "/withdraw"})
    public String moneyForm(jakarta.servlet.http.HttpServletRequest request, Model model) {
        model.addAttribute("moneyRequest", new MoneyRequest());
        populateMoneyForm(model);
        return request.getServletPath().equals("/deposit") ? "deposit" : "withdraw";
    }

    @PostMapping("/deposit")
    public String deposit(@Valid @ModelAttribute("moneyRequest") MoneyRequest request,
            BindingResult result, Model model, RedirectAttributes flash) {
        return processMoney(request, result, model, flash, true);
    }

    @PostMapping("/withdraw")
    public String withdraw(@Valid @ModelAttribute("moneyRequest") MoneyRequest request,
            BindingResult result, Model model, RedirectAttributes flash) {
        return processMoney(request, result, model, flash, false);
    }

    private String processMoney(MoneyRequest request, BindingResult result, Model model,
            RedirectAttributes flash, boolean deposit) {
        String page = deposit ? "deposit" : "withdraw";
        if (result.hasErrors()) {
            populateMoneyForm(model);
            return page;
        }
        try {
            if (deposit) bankService.deposit(request.getAccountNumber(), request.getAmount());
            else bankService.withdraw(request.getAccountNumber(), request.getAmount());
            flash.addFlashAttribute("success", deposit ? "Deposit completed." : "Withdrawal completed.");
        } catch (RuntimeException ex) {
            String reason = failureMessage(ex);
            boolean recorded = recordFailure(deposit ? TransactionType.DEPOSIT : TransactionType.WITHDRAWAL,
                    deposit ? null : request.getAccountNumber(),
                    deposit ? request.getAccountNumber() : null, request.getAmount(), reason);
            flash.addFlashAttribute("error", reason + " All changes were rolled back."
                    + (recorded ? "" : " The failure audit could not be saved."));
        }
        return "redirect:/" + page;
    }

    private void populateMoneyForm(Model model) {
        model.addAttribute("customers", bankService.getAllCustomers().stream()
                .filter(c -> !"999999".equals(c.getAccountNumber())).toList());
    }

    private void populateDashboard(Model model, String keyword) {
        if (!model.containsAttribute("transferRequest"))
            model.addAttribute("transferRequest", new TransferRequest());
        if (!model.containsAttribute("customer")) model.addAttribute("customer", new Customer());
        model.addAttribute("keyword", keyword);
        model.addAttribute("customers", bankService.searchCustomers(keyword));
        model.addAttribute("allCustomers", bankService.getAllCustomers());
        model.addAttribute("totalCustomers", bankService.countCustomers());
        model.addAttribute("totalBalance", bankService.getTotalBalance());
        model.addAttribute("successCount", bankService.countByStatus(TransactionStatus.SUCCESS));
        model.addAttribute("failedCount", bankService.countByStatus(TransactionStatus.FAILED));
        model.addAttribute("transactions", bankService.getTransactions());
        model.addAttribute("feePercent", bankService.getFeeRate().multiply(new BigDecimal("100")));
    }

    private String failureMessage(RuntimeException ex) {
        if (ex instanceof BankException) return ex.getMessage();
        log.error("Bank operation failed", ex);
        return "The bank operation could not be completed.";
    }

    private boolean recordFailure(TransactionType type, String from, String to,
            BigDecimal amount, String reason) {
        try {
            auditService.recordFailure(type, from, to, amount, reason);
            return true;
        } catch (RuntimeException ex) {
            log.error("Failure audit could not be persisted", ex);
            return false;
        }
    }
}
