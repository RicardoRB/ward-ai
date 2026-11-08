package demo.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DescriberTest {
    private static final double FRACTION = 1.5;
    private final Describer describer = new Describer();

    @Test
    void describesPositiveNumbers() {
        Assertions.assertEquals("positive", describer.describe(1), "positive number");
    }

    @Test
    void describesZeroAsNotPositive() {
        Assertions.assertEquals("not positive 0", describer.describe(0), "zero");
    }

    @Test
    void describesText() {
        Assertions.assertEquals("text hi", describer.describe("hi"), "text");
    }

    @Test
    void describesAnythingElseAsUnknown() {
        Assertions.assertEquals("unknown", describer.describe(FRACTION), "other value");
    }
}
