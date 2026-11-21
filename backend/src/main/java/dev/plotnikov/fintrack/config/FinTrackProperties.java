package dev.plotnikov.fintrack.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fintrack")
public record FinTrackProperties(
        String timezone,
        BigDecimal largeAmountEur,
        int lateCorrectionHours,
        BigDecimal largeCorrectionEur,
        boolean demoMode,
        String demoPassword,
        String resetCron) {}
