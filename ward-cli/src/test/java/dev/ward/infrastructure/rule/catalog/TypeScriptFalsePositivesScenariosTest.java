package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The scenarios of story #598: false positives of TypeScript rules on real code. */
class TypeScriptFalsePositivesScenariosTest {
  private static RuleDefinition rule(final String id) {
    return CatalogRuleDefinitions.all().stream()
        .filter(candidate -> candidate.id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  private static List<AnalysisFinding> run(final String id, final String text) {
    return new RuleFixtureRunner().run(
        rule(id),
        "sample.ts",
        text,
        FixtureTypeFacts.of(Optional.empty())
    );
  }

  @Test
  @DisplayName("type parameters are declared names")
  void typeParametersAreDeclaredNames() {
    // Given a generic arrow function <T>(x: T) => x
    final String source = "const identity = <T>(x: T) => x;\nidentity(1);\n";

    // When no-undef runs
    final List<AnalysisFinding> findings = run("ward:typescript/no-undef", source);

    // Then it reports nothing for T
    assertThat(findings).isEmpty();
  }

  @Test
  @DisplayName("overload signatures")
  void overloadSignatures() {
    // Given a function with overload signatures and one implementation
    final String source =
        "function parse(text: string): number;\n"
            + "function parse(text: string, radix: number): number;\n"
            + "function parse(text: string, radix?: number): number {\n"
            + "  return Number.parseInt(text, radix);\n"
            + "}\n";

    // When no-unused-function-parameters runs
    final List<AnalysisFinding> findings =
        run("ward:typescript/no-unused-function-parameters", source);

    // Then it reports no parameter of an overload signature
    assertThat(findings).isEmpty();
  }

  @Test
  @DisplayName("XSS is a security rule")
  void xssIsASecurityRule() {
    // Given the rule ward:typescript/xss
    final RuleDefinition xss = rule("ward:typescript/xss");

    // When the got and Juice Shop corpus tests run (they label every finding of a security rule)
    final String category = xss.category();

    // Then it is a security rule, so every one of its findings is labelled
    assertThat(category).isEqualTo("security");
  }
}
