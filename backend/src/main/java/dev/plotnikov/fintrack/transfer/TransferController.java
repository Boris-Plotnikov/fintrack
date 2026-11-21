package dev.plotnikov.fintrack.transfer;

import dev.plotnikov.fintrack.user.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService service;
    private final CurrentUser currentUser;

    public TransferController(TransferService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    public record CreateRequest(@NotNull Long fromAccountId, @NotNull Long toAccountId,
                                @NotNull @DecimalMin("0.01") BigDecimal amount, @Size(max = 200) String comment) {}

    public record RejectRequest(@Size(max = 200) String reason) {}

    @GetMapping
    public List<TransferDto> list() {
        return service.list();
    }

    @PostMapping
    public TransferDto create(@Valid @RequestBody CreateRequest r) {
        return service.create(r.fromAccountId(), r.toAccountId(), r.amount(), r.comment(), currentUser.get());
    }

    @PostMapping("/{id}/confirm")
    public TransferDto confirm(@PathVariable Long id) {
        return service.confirm(id, currentUser.get());
    }

    @PostMapping("/{id}/reject")
    public TransferDto reject(@PathVariable Long id, @Valid @RequestBody RejectRequest r) {
        return service.reject(id, r.reason(), currentUser.get());
    }
}
