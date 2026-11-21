package dev.plotnikov.fintrack.report;

import dev.plotnikov.fintrack.user.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.YearMonth;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReportController {

    private final ReportService reports;
    private final CsvService csv;
    private final CurrentUser currentUser;

    public ReportController(ReportService reports, CsvService csv, CurrentUser currentUser) {
        this.reports = reports;
        this.csv = csv;
        this.currentUser = currentUser;
    }

    public record ImportRequest(@NotNull Long accountId, @NotBlank @Size(max = 200_000) String content, boolean dryRun) {}

    @GetMapping("/dashboard")
    public ReportService.Dashboard dashboard() {
        return reports.dashboard();
    }

    @GetMapping("/reports/monthly")
    public ReportService.Monthly monthly(@RequestParam String month) {
        CurrentUser.requireManagement(currentUser.get(), "see reports");
        return reports.monthly(YearMonth.parse(month));
    }

    @GetMapping("/reports/export")
    public ResponseEntity<String> export(@RequestParam String month) {
        CurrentUser.requireManagement(currentUser.get(), "export data");
        YearMonth ym = YearMonth.parse(month);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=fintrack-" + ym + ".csv")
                .contentType(new MediaType("text", "csv"))
                .body(csv.export(ym));
    }

    @PostMapping("/import")
    public CsvService.ImportResult importStatement(@Valid @RequestBody ImportRequest request) {
        return csv.importStatement(request.accountId(), request.content(), request.dryRun(), currentUser.get());
    }
}
