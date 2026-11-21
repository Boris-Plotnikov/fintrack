package dev.plotnikov.fintrack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.plotnikov.fintrack.account.Account;
import dev.plotnikov.fintrack.account.AccountRepository;
import dev.plotnikov.fintrack.account.AccountService;
import dev.plotnikov.fintrack.account.CategoryRepository;
import dev.plotnikov.fintrack.anomaly.AnomalyRepository;
import dev.plotnikov.fintrack.anomaly.AnomalyType;
import dev.plotnikov.fintrack.common.ForbiddenException;
import dev.plotnikov.fintrack.operation.OperationDto;
import dev.plotnikov.fintrack.operation.OperationKind;
import dev.plotnikov.fintrack.operation.OperationRequests;
import dev.plotnikov.fintrack.operation.OperationService;
import dev.plotnikov.fintrack.report.CsvService;
import dev.plotnikov.fintrack.report.ReportService;
import dev.plotnikov.fintrack.shift.ShiftService;
import dev.plotnikov.fintrack.transfer.TransferDto;
import dev.plotnikov.fintrack.transfer.TransferService;
import dev.plotnikov.fintrack.transfer.TransferStatus;
import dev.plotnikov.fintrack.user.AppUser;
import dev.plotnikov.fintrack.user.AppUserRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BusinessRulesTest {
    @Autowired OperationService operations;
    @Autowired TransferService transfers;
    @Autowired ShiftService shifts;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accounts;
    @Autowired CategoryRepository categories;
    @Autowired AppUserRepository users;
    @Autowired AnomalyRepository anomalies;
    @Autowired ReportService reports;
    @Autowired CsvService csv;

    AppUser user(String name) {
        return users.findByUsername(name).orElseThrow();
    }

    Account account(String name) {
        return accounts.findByName(name).orElseThrow();
    }

    Long category(String name) {
        return categories.findByName(name).orElseThrow().getId();
    }

    OperationRequests.Create expense(String account, String amount) {
        return new OperationRequests.Create(OperationKind.EXPENSE, account(account).getId(), category("Travel"),
                new BigDecimal(amount), "Taxi", null, null, null);
    }

    @Test
    void expenseReducesBalanceAndFixesRate() {
        Account cash = account("Cash Vienna");
        BigDecimal before = accountService.balance(cash);
        OperationDto op = operations.create(expense("Cash Vienna", "12.40"), user("lena"));
        assertThat(op.amount()).isEqualByComparingTo("-12.40");
        assertThat(op.rateToEur()).isEqualByComparingTo("1");
        assertThat(accountService.balance(cash)).isEqualByComparingTo(before.subtract(new BigDecimal("12.40")));
    }

    @Test
    void cashCannotGoNegative() {
        BigDecimal balance = accountService.balance(account("Cash Vienna"));
        assertThatThrownBy(() -> operations.create(expense("Cash Vienna", balance.add(BigDecimal.ONE).toPlainString()), user("lena")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not enough cash");
    }

    @Test
    void employeeOnlyUsesOwnOfficeAndCannotMakeOwnerOperations() {
        assertThatThrownBy(() -> operations.create(expense("Cash Prague", "10"), user("lena")))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> operations.create(new OperationRequests.Create(OperationKind.OWNER_WITHDRAWAL,
                account("Bank Vienna").getId(), null, new BigDecimal("100"), "x", null, null, null), user("markus")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void categoryMustMatchKind() {
        assertThatThrownBy(() -> operations.create(new OperationRequests.Create(OperationKind.INCOME,
                account("Bank Vienna").getId(), category("Travel"), new BigDecimal("100"), "x", null, null, null), user("markus")))
                .hasMessageContaining("not an income category");
    }

    @Test
    void correctionKeepsOriginalAndCanCancel() {
        OperationDto op = operations.create(expense("Cash Vienna", "50.00"), user("lena"));
        OperationDto corrected = operations.correct(op.id(), new OperationRequests.Correct(new BigDecimal("45.00"), "Receipt shows 45"), user("lena"));
        assertThat(corrected.amount()).isEqualByComparingTo("-50.00");
        assertThat(corrected.effectiveAmount()).isEqualByComparingTo("-45.00");
        assertThat(corrected.status()).isEqualTo("CORRECTED");
        assertThat(corrected.corrections()).hasSize(1);

        OperationDto cancelled = operations.correct(op.id(), new OperationRequests.Correct(BigDecimal.ZERO, "Duplicate entry"), user("markus"));
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.effectiveAmount()).isEqualByComparingTo("0");
    }

    @Test
    void onlyAuthorOrManagerCanCorrect() {
        OperationDto op = operations.create(expense("Cash Munich", "20.00"), user("julia"));
        assertThatThrownBy(() -> operations.correct(op.id(), new OperationRequests.Correct(new BigDecimal("10"), "x"), user("tomas")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void largeAmountCreatesAnomaly() {
        long before = anomalies.findAll().stream().filter(a -> a.getType() == AnomalyType.LARGE_AMOUNT).count();
        operations.create(new OperationRequests.Create(OperationKind.INCOME, account("Bank Vienna").getId(),
                category("Consulting services"), new BigDecimal("12000"), "Big project", "Client", null, null), user("markus"));
        long after = anomalies.findAll().stream().filter(a -> a.getType() == AnomalyType.LARGE_AMOUNT).count();
        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    void transferIsInTransitUntilConfirmed() {
        Account bank = account("Bank Vienna");
        Account cash = account("Cash Vienna");
        BigDecimal bankBefore = accountService.balance(bank);
        BigDecimal cashBefore = accountService.balance(cash);

        TransferDto t = transfers.create(bank.getId(), cash.getId(), new BigDecimal("300"), "Refill", user("markus"));
        assertThat(t.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(accountService.balance(bank)).isEqualByComparingTo(bankBefore.subtract(new BigDecimal("300")));
        assertThat(accountService.balance(cash)).isEqualByComparingTo(cashBefore);

        assertThatThrownBy(() -> transfers.confirm(t.id(), user("julia"))).isInstanceOf(ForbiddenException.class);
        transfers.confirm(t.id(), user("lena"));
        assertThat(accountService.balance(cash)).isEqualByComparingTo(cashBefore.add(new BigDecimal("300")));
        assertThatThrownBy(() -> transfers.confirm(t.id(), user("lena"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectedTransferReturnsMoney() {
        Account bank = account("Bank Munich");
        BigDecimal before = accountService.balance(bank);
        TransferDto t = transfers.create(bank.getId(), account("Cash Munich").getId(), new BigDecimal("100"), null, user("markus"));
        transfers.reject(t.id(), "Wrong account", user("markus"));
        assertThat(accountService.balance(bank)).isEqualByComparingTo(before);
    }

    @Test
    void currencyTransferIsConverted() {
        TransferDto t = transfers.create(account("Bank Vienna").getId(), account("Bank Prague").getId(),
                new BigDecimal("100"), null, user("markus"));
        assertThat(t.amountReceived()).isGreaterThan(new BigDecimal("2000"));
    }

    @Test
    void blindShiftCloseNeedsCommentOutsideTolerance() {
        AppUser lena = user("lena");
        ShiftService.OpenShiftView view = shifts.open(lena);
        assertThat(view.accountsToCount()).extracting(ShiftService.AccountToCount::name).containsExactly("Cash Vienna");
        Account cash = account("Cash Vienna");
        BigDecimal real = accountService.balance(cash);

        List<ShiftService.CountInput> wrong = List.of(new ShiftService.CountInput(cash.getId(), real.subtract(new BigDecimal("20")), null));
        assertThatThrownBy(() -> shifts.close(view.shiftId(), wrong, lena)).hasMessageContaining("comment");

        List<ShiftService.CountInput> explained = List.of(new ShiftService.CountInput(cash.getId(), real.subtract(new BigDecimal("20")), "Paid courier"));
        ShiftService.CloseResult result = shifts.close(view.shiftId(), explained, lena);
        assertThat(result.hasDiscrepancy()).isTrue();
        assertThat(result.counts().get(0).difference()).isEqualByComparingTo("-20.00");
    }

    @Test
    void shiftCloseWithinToleranceIsQuiet() {
        AppUser julia = user("julia");
        ShiftService.OpenShiftView view = shifts.open(julia);
        Account cash = account("Cash Munich");
        BigDecimal counted = accountService.balance(cash).subtract(new BigDecimal("2.00"));
        ShiftService.CloseResult r = shifts.close(view.shiftId(), List.of(new ShiftService.CountInput(cash.getId(), counted, null)), julia);
        assertThat(r.hasDiscrepancy()).isFalse();
        assertThatThrownBy(() -> shifts.open(user("anna"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void monthlyReportAndDashboard() {
        ReportService.Monthly m = reports.monthly(YearMonth.now().minusMonths(1));
        assertThat(m.income()).isPositive();
        assertThat(m.expense()).isPositive();
        assertThat(m.profit()).isEqualByComparingTo(m.income().subtract(m.expense()));
        assertThat(m.incomeByCategory()).isNotEmpty();
        ReportService.Dashboard d = reports.dashboard();
        assertThat(d.accounts()).hasSize(8);
        assertThat(d.pendingTransfers()).isEqualTo(1);
    }

    @Test
    void statementImportWithDryRunAndDuplicates() {
        String file = "date;amount;description;counterparty\n2026-11-03;-49.90;Software subscription;Atlassian\n2026-11-04;1200;Consulting invoice 77;Isar Medical AG\nbad line\n";
        Long bank = account("Bank Vienna").getId();
        CsvService.ImportResult dry = csv.importStatement(bank, file, true, user("markus"));
        assertThat(dry.imported()).isEqualTo(2);
        assertThat(dry.errors()).isEqualTo(1);
        assertThat(dry.rows().get(0).category()).isEqualTo("Software");

        csv.importStatement(bank, file, false, user("markus"));
        CsvService.ImportResult again = csv.importStatement(bank, file, false, user("markus"));
        assertThat(again.duplicates()).isEqualTo(2);
        assertThatThrownBy(() -> csv.importStatement(bank, file, true, user("lena"))).isInstanceOf(ForbiddenException.class);
    }
}
