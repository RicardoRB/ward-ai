package demo.repository;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GreetingRepositoryTest {
    private static final String PREFIX = "Hi, ";

    @Test
    void returnsThePrefix() {
        Assertions.assertEquals(PREFIX, new GreetingRepository(PREFIX).prefix(), "prefix");
    }
}
