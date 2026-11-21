package dev.plotnikov.fintrack.shift;

import dev.plotnikov.fintrack.user.CurrentUser;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shifts")
public class ShiftController {

    private final ShiftService service;
    private final CurrentUser currentUser;

    public ShiftController(ShiftService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    public record CloseRequest(List<ShiftService.CountInput> counts) {}

    @GetMapping("/current")
    public Map<String, Object> current() {
        return service.current(currentUser.get())
                .<Map<String, Object>>map(v -> Map.of("open", true, "shift", v))
                .orElse(Map.of("open", false));
    }

    @PostMapping("/open")
    public ShiftService.OpenShiftView open() {
        return service.open(currentUser.get());
    }

    @PostMapping("/{id}/close")
    public ShiftService.CloseResult close(@PathVariable Long id, @RequestBody CloseRequest request) {
        return service.close(id, request.counts() == null ? List.of() : request.counts(), currentUser.get());
    }

    @GetMapping
    public List<ShiftService.ShiftRow> history() {
        return service.history();
    }
}
