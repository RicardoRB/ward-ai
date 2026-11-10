package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Finds the values that a function declared to return void returns, for the void-return check. */
final class VoidFunctions {

  private static final String FUNCTION_DECLARATION = "functionDeclaration";
  private static final String ARROW_FUNCTION = "arrowFunctionDeclaration";
  private static final String ANONYMOUS_FUNCTION = "anonymousFunction";
  private static final String RETURN = "returnStatement";
  private static final String SEQUENCE = "expressionSequence";
  private static final String TYPE_ANNOTATION = "typeAnnotation";
  private static final String CALL_SIGNATURE = "callSignature";
  private static final Set<String> FUNCTIONS =
      Set.of(FUNCTION_DECLARATION, ARROW_FUNCTION, ANONYMOUS_FUNCTION);
  private static final Pattern VOID_ANNOTATION = Pattern.compile(":\\s*void");

  private VoidFunctions() {}

  /** Returns the values returned by a function whose own return annotation is void, else none. */
  static List<SyntaxNode> returnedValues(final SyntaxNode function) {
    final List<SyntaxNode> values = new ArrayList<>();
    if (declaresVoid(function)) {
      for (final SyntaxNode statement : returnStatements(function)) {
        statement.children().stream()
            .filter(child -> SEQUENCE.equals(child.kind()))
            .forEach(values::add);
      }
    }
    return values;
  }

  private static boolean declaresVoid(final SyntaxNode function) {
    return isVoid(function)
        || function.children().stream()
            .filter(child -> CALL_SIGNATURE.equals(child.kind()))
            .anyMatch(VoidFunctions::isVoid);
  }

  /** Reports whether a node has a direct type annotation of void. */
  private static boolean isVoid(final SyntaxNode node) {
    return node.children().stream()
        .filter(child -> TYPE_ANNOTATION.equals(child.kind()))
        .anyMatch(annotation -> VOID_ANNOTATION.matcher(annotationText(annotation)).matches());
  }

  private static String annotationText(final SyntaxNode annotation) {
    return SyntaxText.of(annotation);
  }

  /** Returns the return statements of a function body, not those of nested functions. */
  private static List<SyntaxNode> returnStatements(final SyntaxNode node) {
    final List<SyntaxNode> out = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if (RETURN.equals(child.kind())) {
        out.add(child);
      } else if (!FUNCTIONS.contains(child.kind())) {
        out.addAll(returnStatements(child));
      }
    }
    return out;
  }
}
