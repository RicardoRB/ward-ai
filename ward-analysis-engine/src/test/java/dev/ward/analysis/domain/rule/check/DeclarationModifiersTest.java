package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Modifiers that open a declaration, as Kotlin puts them inside the declaration node. */
class DeclarationModifiersTest {

  private static final Set<String> MODIFIER_KINDS = Set.of("modifierList", "modifier");
  private static final String NAME = "simpleIdentifier";
  private static final String LOWER_CAMEL = "[a-z][a-zA-Z0-9]*";

  @Test
  void excludedModifierInsideTheDeclarationSkipsIt() {
    final SyntaxNode tree = propertyTree("const", "MAX_RETRIES");

    assertThat(check(Optional.empty(), Optional.of("const")).matches(tree)).isEmpty();
  }

  @Test
  void declarationWithoutTheExcludedModifierIsStillChecked() {
    final SyntaxNode tree = propertyTree("private", "MAX_RETRIES");

    assertThat(check(Optional.empty(), Optional.of("const")).matches(tree)).hasSize(1);
  }

  @Test
  void requiredModifierInsideTheDeclarationIsFound() {
    final SyntaxNode tree = propertyTree("const", "MAX_RETRIES");

    assertThat(check(Optional.of("const"), Optional.empty()).matches(tree)).hasSize(1);
  }

  @Test
  void requiredModifierMissingFromTheDeclarationSkipsIt() {
    final SyntaxNode tree = propertyTree("private", "MAX_RETRIES");

    assertThat(check(Optional.of("const"), Optional.empty()).matches(tree)).isEmpty();
  }

  private static TextPatternCheck check(
      final Optional<String> required, final Optional<String> excluded) {
    return new TextPatternCheck(
        new DeclarationFilter(Set.of("propertyDeclaration"), MODIFIER_KINDS, required, excluded),
        NAME,
        Set.of("modifierList"),
        LOWER_CAMEL,
        "bad {value}");
  }

  /** A property whose modifier list holds one modifier token, then its name. */
  private static SyntaxNode propertyTree(final String modifier, final String name) {
    final int modifierEnd = modifier.length();
    final SyntaxNode token = Trees.leaf("MODIFIER_TOKEN", modifier, 0);
    final SyntaxNode modifierNode = Trees.node("modifier", 0, modifierEnd, 1, token);
    final SyntaxNode modifierList = Trees.node("modifierList", 0, modifierEnd, 1, modifierNode);
    final int nameStart = modifierEnd + 1;
    final SyntaxNode identifier = Trees.leaf(NAME, name, nameStart);
    final SyntaxNode property =
        Trees.node(
            "propertyDeclaration", 0, nameStart + name.length(), 1, modifierList, identifier);
    return Trees.unit(property);
  }
}
