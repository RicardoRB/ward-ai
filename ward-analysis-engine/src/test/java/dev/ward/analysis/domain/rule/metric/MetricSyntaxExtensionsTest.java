package dev.ward.analysis.domain.rule.metric;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The name lists, kind-and-text decisions, path alternatives and backtick stripping of metric
 * rules.
 */
final class MetricSyntaxExtensionsTest {

  private static final String NAME_LIST =
      "measure=identifier-case; node=struct_declaration:cap,class_declaration:cap; name=struct_name|class_name; message={name} should be {expected}";

  private static final String KIND_AND_TEXT =
      "measure=decision-points; node=function_declaration; max=2; name=function_name; decisions=binary_operator#&&,binary_operator#||; message=function {name} has complexity {value}";

  private static final String PARAMETER_ALTERNATIVES =
      "measure=parameters; node=initializer_declaration,function_declaration; max=5; name=initializer_head|function_name; path=function_signature/parameter_clause/parameter_list/parameter|parameter_clause/parameter_list/parameter; message={name} has {value} parameters";

  private static final String BACKTICKS =
      "measure=identifier-case; node=function_declaration:camel; name=function_name; message={name} should be {expected}";

  @Test
  void aNameListReadsWhicheverChildIsPresent() {
    final SyntaxNode root =
        Trees.node(
            "file_input",
            1,
            2,
            Trees.node("struct_declaration", 1, 1, Trees.named("struct_name", "user_store", 1)),
            Trees.node("class_declaration", 2, 2, Trees.named("class_name", "userStore", 2)));

    assertThat(Trees.messages(NAME_LIST, root))
        .containsExactly("user_store should be CapWords", "userStore should be CapWords");
  }

  @Test
  void aKindAndTextDecisionCountsOnlyMatchingNodes() {
    final SyntaxNode function =
        Trees.node(
            "function_declaration",
            1,
            4,
            Trees.named("function_name", "f", 1),
            operator("&&", 2),
            operator("||", 3),
            operator("+", 4));

    assertThat(Trees.messages(KIND_AND_TEXT, function))
        .containsExactly("function f has complexity 3");
  }

  @Test
  void parameterPathAlternativesSumEachMatchingPath() {
    final SyntaxNode initializer =
        Trees.node(
            "initializer_declaration",
            1,
            1,
            Trees.named("initializer_head", "init", 1),
            Trees.node(
                "parameter_clause", 1, 1, Trees.node("parameter_list", 1, 1, parameters(6))));
    final SyntaxNode function =
        Trees.node(
            "function_declaration",
            2,
            2,
            Trees.named("function_name", "g", 2),
            Trees.node(
                "function_signature",
                2,
                2,
                Trees.node(
                    "parameter_clause", 2, 2, Trees.node("parameter_list", 2, 2, parameters(2)))));

    assertThat(
            Trees.messages(
                PARAMETER_ALTERNATIVES, Trees.node("file_input", 1, 2, initializer, function)))
        .containsExactly("init has 6 parameters");
  }

  @Test
  void backticksAreStrippedBeforeTheStyleCheck() {
    final SyntaxNode root =
        Trees.node(
            "file_input",
            1,
            2,
            Trees.node("function_declaration", 1, 1, Trees.named("function_name", "`default`", 1)),
            Trees.node(
                "function_declaration", 2, 2, Trees.named("function_name", "`load_config`", 2)));

    assertThat(Trees.messages(BACKTICKS, root))
        .containsExactly("`load_config` should be lowerCamelCase");
  }

  @Test
  void parsesAlternativeParameterPaths() {
    final MetricRule rule =
        MetricRuleBody.parse(
            "measure=parameters; node=initializer_declaration; max=5; name=initializer_head; path=function_signature/parameter_clause/parameter_list/parameter|parameter_clause/parameter_list/parameter; message=m");

    assertThat(rule.keys().parameterPaths())
        .containsExactly(
            List.of("function_signature", "parameter_clause", "parameter_list", "parameter"),
            List.of("parameter_clause", "parameter_list", "parameter"));
  }

  private static SyntaxNode operator(final String text, final int line) {
    return Trees.node("binary_operator", line, line, Trees.token("NAME", text, line, 1));
  }

  private static SyntaxNode[] parameters(final int count) {
    final SyntaxNode[] items = new SyntaxNode[count];
    for (int index = 0; index < count; index++) {
      items[index] = Trees.node("parameter", 1, 1, Trees.named("name", "p" + index, 1));
    }
    return items;
  }
}
