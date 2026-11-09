package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Runs the Kotlin tree checks over small hand-made syntax trees. */
class KotlinTreeChecksTest {

  private static final String MESSAGE = "m";
  private static final Span SPAN = new Span(1, 1, 1, 2, 0, 1);

  private static SyntaxNode node(final String kind, final SyntaxNode... children) {
    return new SyntaxNode(kind, SPAN, Map.of(), List.of(children));
  }

  private static SyntaxNode leaf(final String text) {
    return new SyntaxNode("token", SPAN, Map.of("text", text), List.of());
  }

  private static SyntaxNode file(final SyntaxNode... declarations) {
    return node("kotlinFile", declarations);
  }

  private static SyntaxNode topLevel(final SyntaxNode declaration) {
    return node("topLevelObject", declaration);
  }

  private static SyntaxNode valLimit() {
    return node("propertyDeclaration", leaf("val"), leaf("LIMIT"), leaf("=10"));
  }

  @Nested
  class MayBeConst {

    @Test
    void reportsATopLevelLiteralProperty() {
      assertThat(new KotlinMayBeConstCheck(MESSAGE).matches(file(topLevel(valLimit())))).hasSize(1);
    }

    @Test
    void reportsAnObjectMemberLiteralProperty() {
      final SyntaxNode object =
          node(
              "objectDeclaration",
              leaf("object"),
              node("classBody", leaf("{"), node("classMemberDeclaration", valLimit()), leaf("}")));

      assertThat(new KotlinMayBeConstCheck(MESSAGE).matches(file(topLevel(object)))).hasSize(1);
    }

    @Test
    void acceptsALocalProperty() {
      final SyntaxNode local = node("statement", node("declaration", valLimit()));

      assertThat(new KotlinMayBeConstCheck(MESSAGE).matches(file(local))).isEmpty();
    }

    @Test
    void acceptsAClassMemberProperty() {
      final SyntaxNode clazz =
          node(
              "classDeclaration",
              leaf("class"),
              node("classBody", leaf("{"), node("classMemberDeclaration", valLimit()), leaf("}")));

      assertThat(new KotlinMayBeConstCheck(MESSAGE).matches(file(topLevel(clazz)))).isEmpty();
    }

    @Test
    void acceptsAnOpenProperty() {
      final SyntaxNode open =
          node("propertyDeclaration", leaf("open"), leaf("val"), leaf("LIMIT"), leaf("=10"));

      assertThat(new KotlinMayBeConstCheck(MESSAGE).matches(file(topLevel(open)))).isEmpty();
    }
  }

  @Nested
  class UnnecessaryApply {

    private static SyntaxNode applyCall(final String receiver, final String lambda) {
      return node(
          "postfixUnaryExpression",
          leaf(receiver),
          leaf(".apply"),
          leaf("{"),
          leaf(lambda),
          leaf("}"));
    }

    @Test
    void reportsAnApplyThatIsAWholeStatement() {
      final SyntaxNode call = applyCall("box", "size");
      final SyntaxNode statement = node("statement", call);

      assertThat(new KotlinUnnecessaryApplyCheck(MESSAGE).matches(file(statement))).hasSize(1);
    }

    @Test
    void acceptsAnAssignedApply() {
      final SyntaxNode call = applyCall("Box()", "size=1");
      final SyntaxNode statement = node("statement", leaf("val"), leaf("box="), call);

      assertThat(new KotlinUnnecessaryApplyCheck(MESSAGE).matches(file(statement))).isEmpty();
    }

    @Test
    void acceptsAReturnedApply() {
      final SyntaxNode call = applyCall("Box()", "size=2");
      final SyntaxNode statement = node("statement", leaf("return"), call);

      assertThat(new KotlinUnnecessaryApplyCheck(MESSAGE).matches(file(statement))).isEmpty();
    }
  }

  @Nested
  class UtilityClass {

    private static SyntaxNode companionMember() {
      return node(
          "classMemberDeclaration", node("companionObject", leaf("companion"), leaf("object")));
    }

    @Test
    void reportsAClassWhoseBodyIsOnlyACompanion() {
      final SyntaxNode clazz =
          node(
              "classDeclaration",
              leaf("class"),
              leaf("Strings"),
              node("classBody", leaf("{"), companionMember(), leaf("}")));

      assertThat(new KotlinUtilityClassCheck(MESSAGE).matches(file(clazz))).hasSize(1);
    }

    @Test
    void acceptsAClassWithAnotherMember() {
      final SyntaxNode clazz =
          node(
              "classDeclaration",
              leaf("class"),
              leaf("Strings"),
              node(
                  "classBody",
                  leaf("{"),
                  companionMember(),
                  node("classMemberDeclaration", node("functionDeclaration", leaf("fun"))),
                  leaf("}")));

      assertThat(new KotlinUtilityClassCheck(MESSAGE).matches(file(clazz))).isEmpty();
    }

    @Test
    void acceptsAnOpenClass() {
      final SyntaxNode clazz =
          node(
              "classDeclaration",
              leaf("open"),
              leaf("class"),
              leaf("Strings"),
              node("classBody", leaf("{"), companionMember(), leaf("}")));

      assertThat(new KotlinUtilityClassCheck(MESSAGE).matches(file(clazz))).isEmpty();
    }
  }

  @Nested
  class LoopJumps {

    private static SyntaxNode jump(final String text) {
      return node("jumpExpression", leaf(text));
    }

    private static SyntaxNode loop(final SyntaxNode... body) {
      return node("forExpression", leaf("for"), node("controlStructureBody", body));
    }

    @Test
    void reportsALoopWithMoreJumpsThanTheLimit() {
      final SyntaxNode loop = loop(jump("break"), jump("continue"));

      assertThat(new KotlinLoopJumpCheck(MESSAGE, 1).matches(file(loop))).hasSize(1);
    }

    @Test
    void countsOnlyTheJumpsOfTheLoopItself() {
      final SyntaxNode inner = loop(jump("break"), jump("break"));
      final SyntaxNode outer = loop(jump("continue"), inner);

      final List<dev.ward.analysis.domain.rule.RuleMatch> found =
          new KotlinLoopJumpCheck(MESSAGE, 1).matches(file(outer));

      assertThat(found).hasSize(1);
      assertThat(found.get(0).message()).isEqualTo(MESSAGE);
    }

    @Test
    void acceptsAReturnAsAJump() {
      final SyntaxNode loop = loop(jump("break"), jump("return"));

      assertThat(new KotlinLoopJumpCheck(MESSAGE, 1).matches(file(loop))).isEmpty();
    }
  }
}
