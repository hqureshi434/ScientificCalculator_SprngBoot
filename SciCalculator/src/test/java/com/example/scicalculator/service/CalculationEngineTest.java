package com.example.scicalculator.service;

import com.example.scicalculator.domain.Operation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the pure calculation engine — no Spring context, no DB.
 *
 * Semantics under test:
 *  - Binary ops (ADD, SUBTRACT, MULTIPLY, DIVIDE, POW) combine currentValue and operand.
 *  - Unary ops (SIN, COS, TAN, LOG, LN, SQRT) act on currentValue and IGNORE operand.
 *  - Trig is in radians; LOG is base 10, LN is natural.
 *  - Every result is rounded HALF_UP to scale 10 (matching the numeric(38,10) columns).
 *  - Mathematically invalid inputs throw ArithmeticException.
 */
class CalculationEngineTest {

    private final CalculationEngine engine = new CalculationEngine();

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    // --- Binary operations -------------------------------------------------

    @Test
    void addsOperandToCurrentValue() {
        assertThat(engine.apply(bd("2"), Operation.ADD, bd("3")))
                .isEqualByComparingTo("5");
    }

    @Test
    void subtractsOperandFromCurrentValue() {
        assertThat(engine.apply(bd("5"), Operation.SUBTRACT, bd("3")))
                .isEqualByComparingTo("2");
    }

    @Test
    void multipliesCurrentValueByOperand() {
        assertThat(engine.apply(bd("4"), Operation.MULTIPLY, bd("2.5")))
                .isEqualByComparingTo("10");
    }

    @Test
    void dividesCurrentValueByOperand() {
        assertThat(engine.apply(bd("10"), Operation.DIVIDE, bd("4")))
                .isEqualByComparingTo("2.5");
    }

    @Test
    void divisionRoundsToScaleTenHalfUp() {
        assertThat(engine.apply(bd("1"), Operation.DIVIDE, bd("3")))
                .isEqualByComparingTo("0.3333333333");
    }

    @Test
    void divisionByZeroThrows() {
        assertThatThrownBy(() -> engine.apply(bd("1"), Operation.DIVIDE, BigDecimal.ZERO))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void raisesCurrentValueToOperandPower() {
        assertThat(engine.apply(bd("2"), Operation.POW, bd("10")))
                .isEqualByComparingTo("1024");
    }

    @Test
    void powerProducingNonRealResultThrows() {
        // (-1) ^ 0.5 is not a real number -> Math.pow yields NaN, which we reject.
        assertThatThrownBy(() -> engine.apply(bd("-1"), Operation.POW, bd("0.5")))
                .isInstanceOf(ArithmeticException.class);
    }

    // --- Unary operations --------------------------------------------------

    @Test
    void squareRootOfCurrentValue() {
        assertThat(engine.apply(bd("9"), Operation.SQRT, BigDecimal.ZERO))
                .isEqualByComparingTo("3");
    }

    @Test
    void squareRootOfNegativeThrows() {
        assertThatThrownBy(() -> engine.apply(bd("-1"), Operation.SQRT, BigDecimal.ZERO))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void sineOfZeroIsZero() {
        assertThat(engine.apply(BigDecimal.ZERO, Operation.SIN, BigDecimal.ZERO))
                .isEqualByComparingTo("0");
    }

    @Test
    void cosineOfZeroIsOne() {
        assertThat(engine.apply(BigDecimal.ZERO, Operation.COS, BigDecimal.ZERO))
                .isEqualByComparingTo("1");
    }

    @Test
    void cosineOfPiIsNegativeOne_provingRadians() {
        // In degrees cos(3.14159...) ~= 0.9985; in radians cos(pi) = -1.
        assertThat(engine.apply(BigDecimal.valueOf(Math.PI), Operation.COS, BigDecimal.ZERO))
                .isEqualByComparingTo("-1");
    }

    @Test
    void tangentOfZeroIsZero() {
        assertThat(engine.apply(BigDecimal.ZERO, Operation.TAN, BigDecimal.ZERO))
                .isEqualByComparingTo("0");
    }

    @Test
    void base10LogOfHundredIsTwo() {
        assertThat(engine.apply(bd("100"), Operation.LOG, BigDecimal.ZERO))
                .isEqualByComparingTo("2");
    }

    @Test
    void logOfNonPositiveThrows() {
        assertThatThrownBy(() -> engine.apply(BigDecimal.ZERO, Operation.LOG, BigDecimal.ZERO))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void naturalLogOfOneIsZero() {
        assertThat(engine.apply(bd("1"), Operation.LN, BigDecimal.ZERO))
                .isEqualByComparingTo("0");
    }

    @Test
    void naturalLogOfNonPositiveThrows() {
        assertThatThrownBy(() -> engine.apply(BigDecimal.ZERO, Operation.LN, BigDecimal.ZERO))
                .isInstanceOf(ArithmeticException.class);
    }

    // --- Cross-cutting behavior -------------------------------------------

    @Test
    void resultIsAlwaysScaledToTenDecimalPlaces() {
        assertThat(engine.apply(bd("2"), Operation.ADD, bd("3")).scale())
                .isEqualTo(10);
    }

    @Test
    void unaryOperationIgnoresOperand() {
        // SQRT(9) is 3 regardless of the operand value passed.
        assertThat(engine.apply(bd("9"), Operation.SQRT, bd("999")))
                .isEqualByComparingTo("3");
    }
}
