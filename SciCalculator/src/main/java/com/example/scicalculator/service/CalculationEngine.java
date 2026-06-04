package com.example.scicalculator.service;

import com.example.scicalculator.domain.Operation;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure, stateless calculator math. Given a running {@code currentValue}, an
 * {@link Operation}, and an {@code operand}, returns the new value.
 *
 * <p>Design notes:
 * <ul>
 *   <li>Binary ops (ADD, SUBTRACT, MULTIPLY, DIVIDE, POW) combine currentValue and operand.
 *   <li>Unary ops (SIN, COS, TAN, LOG, LN, SQRT) act on currentValue and ignore the operand.
 *   <li>Trig is in radians; LOG is base 10, LN is natural.
 *   <li>Every result is rounded HALF_UP to scale 10 to match the {@code numeric(38,10)} columns,
 *       so the engine never produces a value the database would silently truncate.
 *   <li>Transcendental ops (trig, log, pow, sqrt) route through {@code double} and back, so they
 *       carry double's ~15-17 significant-digit precision — exact BigDecimal transcendental math
 *       is deliberately out of scope for this learning exercise.
 * </ul>
 *
 * <p>It is a {@code @Service} so the upcoming {@code @Transactional} calculation service can have
 * it constructor-injected, but it holds no state and needs no Spring context to be unit-tested.
 */
@Service
public class CalculationEngine {

    private static final int RESULT_SCALE = 10;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public BigDecimal apply(BigDecimal currentValue, Operation operation, BigDecimal operand) {
        BigDecimal raw = switch (operation) {
            case ADD -> currentValue.add(operand);
            case SUBTRACT -> currentValue.subtract(operand);
            case MULTIPLY -> currentValue.multiply(operand);
            case DIVIDE -> divide(currentValue, operand);
            case POW -> fromDouble(Math.pow(currentValue.doubleValue(), operand.doubleValue()));
            case SQRT -> sqrt(currentValue);
            case SIN -> fromDouble(Math.sin(currentValue.doubleValue()));
            case COS -> fromDouble(Math.cos(currentValue.doubleValue()));
            case TAN -> fromDouble(Math.tan(currentValue.doubleValue()));
            case LOG -> logarithm(currentValue, 10.0);
            case LN -> logarithm(currentValue, Math.E);
        };
        return raw.setScale(RESULT_SCALE, ROUNDING);
    }

    private static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        if (divisor.signum() == 0) {
            throw new ArithmeticException("Cannot divide by zero");
        }
        return dividend.divide(divisor, RESULT_SCALE, ROUNDING);
    }

    private static BigDecimal sqrt(BigDecimal value) {
        if (value.signum() < 0) {
            throw new ArithmeticException("Cannot take the square root of a negative number");
        }
        return fromDouble(Math.sqrt(value.doubleValue()));
    }

    private static BigDecimal logarithm(BigDecimal value, double base) {
        if (value.signum() <= 0) {
            throw new ArithmeticException("Cannot take the logarithm of a non-positive number");
        }
        // log_base(x) = ln(x) / ln(base); base 10 uses Math.log10 for a touch more accuracy.
        double result = base == 10.0
                ? Math.log10(value.doubleValue())
                : Math.log(value.doubleValue()) / Math.log(base);
        return fromDouble(result);
    }

    /**
     * Wraps a double result, rejecting NaN/infinite values (e.g. {@code (-1)^0.5}, which has no
     * real result) as an {@link ArithmeticException} rather than letting them reach the database.
     */
    private static BigDecimal fromDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new ArithmeticException("Operation did not produce a finite real number");
        }
        return BigDecimal.valueOf(value);
    }
}
