package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The subclass slots that repeat a base class slot of the same file. */
final class RedefinedSlotsInSubclassCheckTest {

  private static final RedefinedSlotsInSubclassCheck CHECK = new RedefinedSlotsInSubclassCheck("m");

  @Test
  void aSlotRepeatedFromTheBaseIsReported() {
    assertThat(
            CHECK.matches(
                ParsedPython.tree(
                    "class Base:\n    __slots__ = ('a',)\n\nclass Child(Base):\n    __slots__ = ('a', 'b')\n")))
        .hasSize(1);
  }

  @Test
  void distinctSlotsAreNotReported() {
    assertThat(
            CHECK.matches(
                ParsedPython.tree(
                    "class Base:\n    __slots__ = ('a',)\n\nclass Child(Base):\n    __slots__ = ('b',)\n")))
        .isEmpty();
  }

  @Test
  void aBaseOutsideTheFileOrWithoutSlotsIsNotReported() {
    assertThat(CHECK.matches(ParsedPython.tree("class Child(Other):\n    __slots__ = ('a',)\n")))
        .isEmpty();
    assertThat(
            CHECK.matches(
                ParsedPython.tree(
                    "class Base:\n    x = 1\n\nclass Child(Base):\n    __slots__ = ('a',)\n")))
        .isEmpty();
  }

  @Test
  void aSubclassWithoutOwnSlotsIsNotReported() {
    assertThat(
            CHECK.matches(
                ParsedPython.tree(
                    "class Base:\n    __slots__ = (\"a\",)\n\nclass Child(Base):\n    x = 1\n")))
        .isEmpty();
  }
}
