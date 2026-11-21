package dev.plotnikov.fintrack.audit;

import dev.plotnikov.fintrack.user.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditRepository repository;
    private final CurrentUser currentUser;

    public AuditController(AuditRepository repository, CurrentUser currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @GetMapping
    public Page<AuditEntry> list(@RequestParam(defaultValue = "0") int page) {
        CurrentUser.requireManagement(currentUser.get(), "see the audit log");
        return repository.findAllByOrderByAtDesc(PageRequest.of(Math.max(page, 0), 50));
    }
}
