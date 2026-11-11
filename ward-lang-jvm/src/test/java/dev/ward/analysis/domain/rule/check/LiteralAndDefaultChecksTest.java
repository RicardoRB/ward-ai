package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

/** The magic-number and explicit-initialization checks on small trees. */
class LiteralAndDefaultChecksTest extends TreeBuilders {

  private static final double HEX_VALUE = 31;
  private static final double BINARY_VALUE = 5;
  private static final double OCTAL_VALUE = 15;
  private static final double THOUSAND = 1000;
  private static final double FRACTION = 2.5;
  private static final String DECIMAL = "DECIMAL_LITERAL";
  private static final String FIELD = "fieldDeclaration";
  private static final String DECLARATOR = "variableDeclarator";
  private static final String INITIALIZER = "variableInitializer";
  private static final String TYPE = "typeType";
  private static final String ANNOTATION_KIND = "annotation";

  @Test
  void numberInAnExpressionIsMagicUnlessIgnored() {
    final SyntaxNode literal = t(DECIMAL, "42");
    assertThat(magic(List.of(), false, false, false).check(unit(literal), FILE)).hasSize(1);
    assertThat(magic(List.of("42"), false, false, false).check(unit(literal), FILE)).isEmpty();
    assertThat(magic(List.of("-42"), false, false, false).check(unit(literal), FILE)).isEmpty();
  }

  @Test
  void numberInAConstantIsNeverMagic() {
    final SyntaxNode constant = n("constDeclaration", n("constantDeclarator", t(DECIMAL, "42")));
    assertThat(magic(List.of(), false, false, false).check(unit(constant), FILE)).isEmpty();
    final SyntaxNode staticFinal =
        n(
            CLASS_BODY_DECLARATION,
            n(MODIFIER, t("'static'", "static")),
            n(MODIFIER, t("'final'", "final")),
            n(FIELD, n(DECLARATOR, t(DECIMAL, "42"))));
    assertThat(magic(List.of(), false, false, false).check(unit(staticFinal), FILE)).isEmpty();
    final SyntaxNode plain =
        n(
            CLASS_BODY_DECLARATION,
            n(MODIFIER, t("'static'", "static")),
            n(FIELD, n(DECLARATOR, t(DECIMAL, "42"))));
    assertThat(magic(List.of(), false, false, false).check(unit(plain), FILE)).hasSize(1);
  }

  @Test
  void optionsIgnoreFieldsAnnotationsAndHashCode() {
    final SyntaxNode field = n(FIELD, t(DECIMAL, "42"));
    assertThat(magic(List.of(), true, false, false).check(unit(field), FILE)).isEmpty();
    assertThat(magic(List.of(), false, false, false).check(unit(field), FILE)).hasSize(1);
    final SyntaxNode annotation = n(ANNOTATION_KIND, t(DECIMAL, "42"));
    assertThat(magic(List.of(), false, true, false).check(unit(annotation), FILE)).isEmpty();
    final SyntaxNode hash = n(METHOD_DECLARATION, id("hashCode"), n(BLOCK, t(DECIMAL, "31")));
    assertThat(magic(List.of(), false, false, true).check(unit(hash), FILE)).isEmpty();
    assertThat(magic(List.of(), false, false, false).check(unit(hash), FILE)).hasSize(1);
    final SyntaxNode other = n(METHOD_DECLARATION, id("size"), n(BLOCK, t(DECIMAL, "31")));
    assertThat(magic(List.of(), false, false, true).check(unit(other), FILE)).hasSize(1);
  }

  @Test
  void ignoredNumberThatIsNotANumberIsRejected() {
    assertThatThrownBy(() -> magic(List.of("Sample"), false, false, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a number");
  }

  @Test
  void literalsInEveryNotationHaveAValue() {
    assertThat(MagicNumberCheck.value("0x1F")).isEqualTo(OptionalDouble.of(HEX_VALUE));
    assertThat(MagicNumberCheck.value("0b101")).isEqualTo(OptionalDouble.of(BINARY_VALUE));
    assertThat(MagicNumberCheck.value("017")).isEqualTo(OptionalDouble.of(OCTAL_VALUE));
    assertThat(MagicNumberCheck.value("1_000L")).isEqualTo(OptionalDouble.of(THOUSAND));
    assertThat(MagicNumberCheck.value("2.5f")).isEqualTo(OptionalDouble.of(FRACTION));
    assertThat(MagicNumberCheck.value("0")).isEqualTo(OptionalDouble.of(0));
    assertThat(MagicNumberCheck.value("abc")).isEmpty();
  }

  @Test
  void defaultInitializersAreReported() {
    assertThat(findings(field("int", t(DECIMAL, "0")), false)).hasSize(1);
    assertThat(findings(field("boolean", t("'false'", "false")), false)).hasSize(1);
    assertThat(findings(field("char", t("CHAR_LITERAL", "'\\0'")), false)).hasSize(1);
    assertThat(findings(field("Object", t("NULL", "null")), false)).hasSize(1);
  }

  @Test
  void otherInitializersAndTheObjectOnlyOptionAreRespected() {
    assertThat(findings(field("int", t(DECIMAL, "5")), false)).isEmpty();
    assertThat(findings(field("boolean", t("'true'", "true")), false)).isEmpty();
    assertThat(findings(field("int", t(DECIMAL, "0")), true)).isEmpty();
    assertThat(findings(field("Object", t("NULL", "null")), true)).hasSize(1);
    final SyntaxNode uninitialized = n(FIELD, n(TYPE, t("int", "int")), n(DECLARATOR, id("x")));
    assertThat(new ExplicitInitializationCheck(false, MESSAGE).check(unit(uninitialized), FILE))
        .isEmpty();
  }

  private static MagicNumberCheck magic(
      final List<String> ignored,
      final boolean fields,
      final boolean annotations,
      final boolean hashCode) {
    return new MagicNumberCheck(ignored, fields, annotations, hashCode, MESSAGE);
  }

  private static List<?> findings(final SyntaxNode field, final boolean onlyObjects) {
    return new ExplicitInitializationCheck(onlyObjects, MESSAGE).check(unit(field), FILE);
  }

  private static SyntaxNode field(final String type, final SyntaxNode value) {
    return n(
        FIELD, n(TYPE, t(TOKEN_IDENTIFIER, type)), n(DECLARATOR, id("x"), n(INITIALIZER, value)));
  }
}
