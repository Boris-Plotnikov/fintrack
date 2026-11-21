package dev.plotnikov.fintrack.anomaly;

import dev.plotnikov.fintrack.user.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/anomalies")
public class AnomalyController {

    private final AnomalyService service;
    private final CurrentUser currentUser;

    public AnomalyController(AnomalyService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    public record DecisionRequest(@NotNull AnomalyStatus status, @Size(max = 400) String note) {}

    @GetMapping
    public List<Anomaly> list(@RequestParam(required = false) AnomalyStatus status) {
        return service.list(status);
    }

    @PostMapping("/{id}/decision")
    public Anomaly decide(@PathVariable Long id, @Valid @RequestBody DecisionRequest request) {
        return service.decide(id, request.status(), request.note(), currentUser.get());
    }
}
