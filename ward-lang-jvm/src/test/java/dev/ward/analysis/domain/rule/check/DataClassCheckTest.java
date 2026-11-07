package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataClassCheckTest extends ClassMemberTrees {

  private static final String MESSAGE = "{value}";
  private static final String DATA_CLASS = "Point";
  private static final String GETTER = "getA";
  private static final String SETTER = "setA";

  @Test
  void reportsDataClassWithPublicFieldsAndAccessors() {
    final SyntaxNode root =
        compilationUnit(
            classNamed(
                DATA_CLASS,
                member(field("x"), PUBLIC),
                member(field("y"), PUBLIC),
                member(field("z"), PUBLIC),
                member(method("getX", 0, 0), PUBLIC),
                member(method("setX", 1, 0), PUBLIC)));

    assertThat(dataClass(root)).hasSize(1);
  }

  @Test
  void reportsDataClassAtTheMidComplexityLevel() {
    final SyntaxNode root =
        compilationUnit(
            classNamed(
                DATA_CLASS,
                member(field("a"), PUBLIC),
                member(field("b"), PUBLIC),
                member(field("c"), PUBLIC),
                member(field("d"), PUBLIC),
                member(method(GETTER, 0, 0), PUBLIC),
                member(method(SETTER, 1, 0), PUBLIC),
                member(method("heavy", 0, 30), PRIVATE)));

    assertThat(dataClass(root)).hasSize(1);
  }

  @Test
  void skipsDataClassAtHighComplexityOrWithFewRevealedMembers() {
    final SyntaxNode highComplexity =
        compilationUnit(
            classNamed(
                DATA_CLASS,
                member(field("a"), PUBLIC),
                member(field("b"), PUBLIC),
                member(field("c"), PUBLIC),
                member(field("d"), PUBLIC),
                member(method(GETTER, 0, 0), PUBLIC),
                member(method(SETTER, 1, 0), PUBLIC),
                member(method("heavy", 0, 46), PRIVATE)));
    final SyntaxNode fewRevealed =
        compilationUnit(
            classNamed(
                DATA_CLASS, member(field("a"), PUBLIC), member(method(GETTER, 0, 0), PUBLIC)));

    assertThat(dataClass(highComplexity)).isEmpty();
    assertThat(dataClass(fewRevealed)).isEmpty();
  }

  @Test
  void skipsDataClassWithFunctionalPublicMethods() {
    final SyntaxNode root =
        compilationUnit(
            classNamed(
                "Calculator",
                member(field("x"), PUBLIC),
                member(field("y"), PUBLIC),
                member(field("z"), PUBLIC),
                member(method(GETTER, 0, 0), PUBLIC),
                member(method(SETTER, 1, 0), PUBLIC),
                member(method("compute", 0, 0), PUBLIC)));

    assertThat(dataClass(root)).isEmpty();
  }

  private static List<String> dataClass(final SyntaxNode root) {
    return Trees.messages(new DataClassCheck(MESSAGE).check(root, SourceFile.UNKNOWN));
  }
}
