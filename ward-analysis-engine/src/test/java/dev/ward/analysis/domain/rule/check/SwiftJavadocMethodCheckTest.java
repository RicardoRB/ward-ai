package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SwiftJavadocMethodCheckTest {

  private static final int VALUE_10 = 10;
  private static final int VALUE_15 = 15;
  private static final int VALUE_20 = 20;
  private static final int VALUE_21 = 21;
  private static final int VALUE_25 = 25;
  private static final int VALUE_40 = 40;
  private static final int VALUE_41 = 41;
  private static final int VALUE_45 = 45;
  private static final int VALUE_46 = 46;
  private static final int VALUE_47 = 47;
  private static final int VALUE_50 = 50;
  private static final int VALUE_60 = 60;
  private static final int VALUE_90 = 90;
  private static final int VALUE_100 = 100;

  private static SyntaxNode documentedFunction(final String comment, final String... parameters) {
    return documentedFunction(comment, false, false, parameters);
  }

  private static SyntaxNode documentedFunction(
      final String comment,
      final boolean returns,
      final boolean throwing,
      final String... parameters) {
    final SyntaxNode[] params = new SyntaxNode[parameters.length];
    for (int index = 0; index < parameters.length; index++) {
      params[index] =
          SwiftTestNodes.node(
              "parameter",
              VALUE_20 + index,
              VALUE_25 + index,
              SwiftTestNodes.node(
                  "local_parameter_name",
                  VALUE_20 + index,
                  VALUE_21 + index,
                  SwiftTestNodes.leaf("label_identifier", parameters[index], VALUE_20 + index)));
    }
    final SyntaxNode[] signature = new SyntaxNode[params.length + 2];
    System.arraycopy(params, 0, signature, 0, params.length);
    signature[params.length] =
        returns
            ? SwiftTestNodes.node("function_result", VALUE_40, VALUE_45)
            : SwiftTestNodes.node("filler", VALUE_40, VALUE_41);
    signature[params.length + 1] =
        throwing
            ? SwiftTestNodes.node("throws_clause", VALUE_46, VALUE_50)
            : SwiftTestNodes.node("filler", VALUE_46, VALUE_47);
    final SyntaxNode function =
        SwiftTestNodes.node(
            "function_declaration",
            VALUE_60,
            VALUE_100,
            SwiftTestNodes.node("function_signature", VALUE_15, VALUE_90, signature));
    final SyntaxNode doc =
        new SyntaxNode(
            "comment", SwiftTestNodes.span(0, VALUE_10), Map.of("text", comment), List.of());
    return SwiftTestNodes.node("file", 0, VALUE_100, doc, function);
  }

  private static List<RuleMatch> javadoc(final SyntaxNode file) {
    return new JavadocMethodCheck("missing {value}").matches(file);
  }

  @Test
  void javadocMethodReportsMissingParameterTag() {
    final List<RuleMatch> found =
        javadoc(documentedFunction("/** - Parameter a: first */", "a", "b"));

    assertThat(found).hasSize(1);
    assertThat(found.get(0).message()).isEqualTo("missing - Parameter b:");
  }

  @Test
  void javadocMethodReportsMissingReturnAndThrowsTags() {
    final List<RuleMatch> found =
        javadoc(documentedFunction("/** - Parameter a: first */", true, true, "a"));

    assertThat(found)
        .extracting(RuleMatch::message)
        .containsExactly("missing - Returns:", "missing - Throws:");
  }

  @Test
  void javadocMethodAcceptsAFullyDocumentedFunction() {
    assertThat(
            javadoc(
                documentedFunction(
                    "/** - Parameter a: first - Returns: sum - Throws: never */", true, true, "a")))
        .isEmpty();
  }

  @Test
  void javadocMethodLeavesUndocumentedFunctionsToTheMissingDocumentationRule() {
    final SyntaxNode undocumented =
        SwiftTestNodes.node(
            "file",
            0,
            VALUE_100,
            SwiftTestNodes.node(
                "function_declaration",
                VALUE_60,
                VALUE_100,
                SwiftTestNodes.node(
                    "function_signature",
                    VALUE_15,
                    VALUE_90,
                    SwiftTestNodes.node(
                        "parameter",
                        VALUE_20,
                        VALUE_25,
                        SwiftTestNodes.node(
                            "local_parameter_name",
                            VALUE_20,
                            VALUE_21,
                            SwiftTestNodes.leaf("label_identifier", "a", VALUE_20))))));

    assertThat(javadoc(undocumented)).isEmpty();
  }
}
