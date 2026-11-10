package demo.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GreetingTest {
    private static final String NAME = "Ada";

    @Test
    void personalWhenTheTextNamesThePerson() {
        Assertions.assertTrue(new Greeting(NAME, "Hello, Ada").personal(), "names the person");
    }

    @Test
    void notPersonalOtherwise() {
        Assertions.assertFalse(new Greeting(NAME, "Hello, world").personal(), "names nobody");
    }
}
