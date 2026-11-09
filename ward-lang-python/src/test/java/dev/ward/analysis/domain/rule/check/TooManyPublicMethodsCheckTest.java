package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The classes with more public methods than the limit, and the methods that do not count. */
final class TooManyPublicMethodsCheckTest {

  private static final TooManyPublicMethodsCheck CHECK = new TooManyPublicMethodsCheck(1, "m");

  @Test
  void aClassOverTheLimitIsReported() {
    assertThat(
            CHECK.matches(
                ParsedPython.tree(
                    "class C:\n    def a(self):\n        pass\n    def b(self):\n        pass\n")))
        .hasSize(1);
  }

  @Test
  void privateMethodsDoNotCount() {
    assertThat(
            CHECK.matches(
                ParsedPython.tree(
                    "class C:\n    def a(self):\n        pass\n    def _b(self):\n        pass\n")))
        .isEmpty();
  }

  @Test
  void decoratedMethodsCountAndNestedClassMethodsDoNot() {
    assertThat(
            CHECK.matches(
                ParsedPython.tree(
                    "class C:\n    @property\n    def a(self):\n        pass\n"
                        + "    def b(self):\n        pass\n")))
        .hasSize(1);
    assertThat(
            CHECK.matches(
                ParsedPython.tree(
                    "class C:\n    def a(self):\n        pass\n"
                        + "    class D:\n        def b(self):\n            pass\n")))
        .isEmpty();
  }
}
