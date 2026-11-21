package dev.plotnikov.fintrack.demo;

import dev.plotnikov.fintrack.config.FinTrackProperties;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class DemoController implements ApplicationRunner {
    private final DemoDataService demo;
    private final FinTrackProperties properties;

    public DemoController(DemoDataService demo, FinTrackProperties properties) {
        this.demo = demo;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.demoMode() && demo.isEmpty()) {
            demo.seed();
        }
    }

    @Scheduled(cron = "${fintrack.reset-cron}", zone = "${fintrack.timezone}")
    public void nightlyReset() {
        if (properties.demoMode()) {
            demo.reset();
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> reset() {
        if (!properties.demoMode()) {
            return ResponseEntity.status(403).body(Map.of("error", "Not in demo mode"));
        }
        demo.reset();
        return ResponseEntity.ok(Map.of("status", "reset"));
    }
}
