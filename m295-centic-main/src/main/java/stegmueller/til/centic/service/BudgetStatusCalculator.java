package stegmueller.til.centic.service;

import org.springframework.stereotype.Component;
import stegmueller.til.centic.model.BudgetStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.regex.Pattern;

/**
 * Budget-Ampel: bewertet die Ausgaben eines Monats gegenüber dem Limit
 * und erkennt im laufenden Monat eine drohende Überschreitung per linearer Prognose.
 */
@Component
public class BudgetStatusCalculator {

    private static final Pattern MONTH_PATTERN = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");
    private static final BigDecimal WARN_RATIO = new BigDecimal("0.80");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final Clock clock;

    public BudgetStatusCalculator(Clock clock) {
        this.clock = clock;
    }

    public BudgetStatus evaluate(BigDecimal limit, BigDecimal spent, String month) {
        validateLimit(limit);
        if (spent == null) {
            throw new IllegalArgumentException("Ausgaben dürfen nicht null sein");
        }
        if (spent.signum() < 0) {
            throw new IllegalArgumentException("Ausgaben dürfen nicht negativ sein");
        }
        YearMonth yearMonth = parseMonth(month);

        if (spent.compareTo(limit) > 0) {
            return BudgetStatus.ROT;
        }
        if (spent.compareTo(limit.multiply(WARN_RATIO)) >= 0) {
            return BudgetStatus.GELB;
        }
        if (yearMonth.equals(YearMonth.now(clock)) && forecastExceedsLimit(limit, spent, yearMonth)) {
            return BudgetStatus.PROGNOSE_UEBERSCHREITUNG;
        }
        return BudgetStatus.GRUEN;
    }

    public BigDecimal usagePercent(BigDecimal limit, BigDecimal spent) {
        validateLimit(limit);
        return spent.multiply(HUNDRED).divide(limit, 2, RoundingMode.HALF_UP);
    }

    // spent / heute * Monatslänge > limit, ohne Division umgestellt: spent * Monatslänge > limit * heute
    private boolean forecastExceedsLimit(BigDecimal limit, BigDecimal spent, YearMonth yearMonth) {
        int today = LocalDate.now(clock).getDayOfMonth();
        BigDecimal projected = spent.multiply(BigDecimal.valueOf(yearMonth.lengthOfMonth()));
        return projected.compareTo(limit.multiply(BigDecimal.valueOf(today))) > 0;
    }

    private static void validateLimit(BigDecimal limit) {
        if (limit == null) {
            throw new IllegalArgumentException("Limit darf nicht null sein");
        }
        if (limit.signum() <= 0) {
            throw new IllegalArgumentException("Limit muss grösser als 0 sein");
        }
    }

    private static YearMonth parseMonth(String month) {
        if (month == null) {
            throw new IllegalArgumentException("Monat darf nicht null sein");
        }
        if (!MONTH_PATTERN.matcher(month).matches()) {
            throw new IllegalArgumentException("Ungültiger Monat: '" + month + "' (erwartet yyyy-MM)");
        }
        return YearMonth.parse(month);
    }
}
