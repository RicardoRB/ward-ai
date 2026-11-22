package dev.ward.analysis.frontend.antlr.format.csharp;

import dev.ward.analysis.frontend.antlr.csharp.CSharpParser;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/** Decides the {@link CSharpRole} of a terminal from the rule that holds it. */
final class CSharpRoles {
  private static final String OPERATOR_EXPRESSION = "operator_expression";
  private static final String SHIFT_EXPRESSION = "shift_expression";
  private static final String QUESTION = "?";
  private static final String BRACE_OPEN = "{";
  private static final String BRACE_CLOSE = "}";
  private static final String COLON = ":";
  private static final String ANGLE_OPEN = "<";
  private static final String ANGLE_CLOSE = ">";
  private static final Set<String> BINARY_PARENTS = Set.of(OPERATOR_EXPRESSION, SHIFT_EXPRESSION);
  private static final Set<String> PREFIX_PARENTS =
      Set.of(
          "unary_expression",
          "non_sign_unary_expression",
          "pre_increment_expression",
          "pre_decrement_expression",
          "logical_negation_operator"
      );
  private static final Set<String> GENERIC_PARENTS =
      Set.of("type_argument_list", "type_parameter_list");
  private static final Set<String> COLON_PARENTS =
      Set.of(
          "class_base",
          "constructor_initializer",
          "type_parameter_constraints_clause",
          "enum_declaration"
      );
  private static final Set<String> NULL_CONDITIONAL_PARENTS =
      Set.of("null_conditional_member_access", "null_conditional_element_access");
  private static final Map<String, CSharpRole> WHOLE_RULE =
      Map.of(
          "null_forgiving_operator",
          CSharpRole.POSTFIX,
          "nullable_type_annotation",
          CSharpRole.NULLABLE,
          "spread_element",
          CSharpRole.SPREAD,
          "slice_pattern",
          CSharpRole.SPREAD,
          "overloadable_operator",
          CSharpRole.OPERATOR_NAME
      );

  private CSharpRoles() {}

  /**
   * Returns the role of a terminal.
   *
   * @param context the rule that holds the terminal
   * @param position the terminal's position among the rule's children
   * @param text the terminal's text
   * @return the role, {@link CSharpRole#NONE} when the rule gives none
   */
  static CSharpRole of(final ParserRuleContext context, final int position, final String text) {
    final String rule = ruleOf(context);
    if (BINARY_PARENTS.contains(rule)) {
      return position > 0 && !BRACE_OPEN.equals(text) && !BRACE_CLOSE.equals(text)
          ? CSharpRole.BINARY
          : CSharpRole.NONE;
    }
    if (PREFIX_PARENTS.contains(rule)) {
      return position == 0 ? CSharpRole.PREFIX : CSharpRole.NONE;
    }
    if (GENERIC_PARENTS.contains(rule)) {
      return genericRole(text);
    }
    final CSharpRole whole = WHOLE_RULE.get(rule);
    return whole == null ? otherRole(context, rule, position, text) : whole;
  }

  /**
   * Returns the name of the rule of a tree node.
   *
   * @param tree the node
   * @return the rule name, empty for a terminal
   */
  static String ruleOf(final ParseTree tree) {
    return tree instanceof ParserRuleContext rule
        ? CSharpParser.ruleNames[rule.getRuleIndex()]
        : "";
  }

  private static CSharpRole genericRole(final String text) {
    if (ANGLE_OPEN.equals(text)) {
      return CSharpRole.GENERIC_OPEN;
    }
    return ANGLE_CLOSE.equals(text) ? CSharpRole.GENERIC_CLOSE : CSharpRole.NONE;
  }

  private static CSharpRole otherRole(
      final ParserRuleContext context,
      final String rule,
      final int position,
      final String text
  ) {
    return switch (rule) {
      case "conditional_expression" ->
          QUESTION.equals(text) || COLON.equals(text) ? CSharpRole.BINARY : CSharpRole.NONE;
      case "primary_expression" -> primaryRole(position, text);
      case "cast_expression" -> ")".equals(text) ? CSharpRole.CAST_CLOSE : CSharpRole.NONE;
      case "array_initializer" -> arrayBrace(context, text);
      default -> declarationRole(rule, text);
    };
  }

  private static CSharpRole declarationRole(final String rule, final String text) {
    if (COLON_PARENTS.contains(rule) && COLON.equals(text)) {
      return CSharpRole.COLON_SPACED;
    }
    return NULL_CONDITIONAL_PARENTS.contains(rule) && QUESTION.equals(text)
        ? CSharpRole.NULL_CONDITIONAL
        : CSharpRole.NONE;
  }

  private static CSharpRole primaryRole(final int position, final String text) {
    if (QUESTION.equals(text)) {
      return CSharpRole.NULL_CONDITIONAL;
    }
    final boolean postfix = "++".equals(text) || "--".equals(text);
    return postfix && position > 0 ? CSharpRole.POSTFIX : CSharpRole.NONE;
  }

  /**
   * A brace of an array initializer. The grammar also reads {@code new Point { X = 1 }} as an array
   * creation, so only an initializer that follows brackets, or has no creation around it, counts.
   */
  private static CSharpRole arrayBrace(final ParserRuleContext context, final String text) {
    if (!BRACE_OPEN.equals(text) && !BRACE_CLOSE.equals(text)) {
      return CSharpRole.NONE;
    }
    final ParserRuleContext creation = context.getParent();
    if (!"array_creation_expression".equals(ruleOf(creation))) {
      return CSharpRole.ARRAY_BRACE;
    }
    final StringBuilder before = new StringBuilder();
    for (final ParseTree child : creation.children) {
      if (child == context) {
        break;
      }
      before.append(child.getText());
    }
    return before.indexOf("[") >= 0 ? CSharpRole.ARRAY_BRACE : CSharpRole.NONE;
  }
}
