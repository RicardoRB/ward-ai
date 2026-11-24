package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The reads of {@code this} inside the lambdas of {@code run}, {@code with} and {@code apply}: a
 * {@code this} written out, and a call written without a receiver, whose receiver is the lambda's.
 * Each read names the lambda as its declaration, where {@link KotlinLambdaParameters} writes the
 * variable.
 *
 * <p>shortcut: a call with no receiver reads {@code this} unless its name is a function of the same
 * file or a common standard-library function, because no type facts tell a member of the receiver
 * from an outer function; upgrade with type facts when a rule needs it. Only a call without
 * arguments reads it, such as {@code trim()}: the read of a call with arguments would sit outside
 * the argument of a sanitizer written without a receiver and leak past it. Member properties of
 * the receiver, such as {@code length}, are not read.
 */
final class KotlinThisReads {
  static final String THIS = "this";
  static final Set<String> RECEIVER_FUNCTIONS = Set.of("run", "with", "apply");
  private static final Set<String> STANDARD =
      Set.of(
          "println",
          "print",
          "listOf",
          "mutableListOf",
          "setOf",
          "mutableSetOf",
          "mapOf",
          "mutableMapOf",
          "arrayOf",
          "emptyList",
          "emptyMap",
          "emptySet",
          "require",
          "check",
          "error",
          "lazy",
          "repeat",
          "run",
          "with",
          "let",
          "also",
          "apply",
          "takeIf",
          "takeUnless",
          "maxOf",
          "minOf",
          "readLine",
          "readln",
          "readlnOrNull",
          "TODO",
          "synchronized"
      );
  private static final String FUNCTION = "functionDeclaration";
  private static final String POSTFIX = "postfixUnaryExpression";
  private static final String CALL_SUFFIX = "callSuffix";
  private static final String MEMBER = "memberAccessOperator";
  private static final String ANNOTATED = "annotatedLambda";
  private static final String LAMBDA = "functionLiteral";
  private static final Set<String> OTHER_BODIES =
      Set.of(
          "functionDeclaration",
          "classDeclaration",
          "objectDeclaration",
          "objectLiteral",
          "companionObject",
          "secondaryConstructor",
          "anonymousInitializer",
          "typeAlias"
      );

  private final Set<String> declared;
  private final List<VariableAccess> reads = new ArrayList<>();

  private KotlinThisReads(final Set<String> declared) {
    this.declared = declared;
  }

  /** The names of the functions that the file declares. */
  static Set<String> functions(final SyntaxNode tree) {
    final Set<String> names = new HashSet<>();
    collect(tree, names);
    return names;
  }

  private static void collect(final SyntaxNode node, final Set<String> names) {
    if (FUNCTION.equals(node.kind())) {
      final SyntaxNode name = KotlinFlowSyntax.child(node, "identifier");
      if (name != null) {
        names.add(KotlinFlowSyntax.text(name));
      }
    }
    node.children().forEach(child -> collect(child, names));
  }

  /** The reads of {@code this} in the body of {@code owner}, in source order. */
  static List<VariableAccess> of(final SyntaxNode owner, final Set<String> declared) {
    final KotlinThisReads found = new KotlinThisReads(declared);
    final Span receiver =
        FUNCTION.equals(owner.kind()) ? KotlinExtensions.receiverOf(owner).orElse(null) : null;
    owner.children().forEach(child -> found.walk(child, receiver, false));
    return List.copyOf(found.reads);
  }

  private void walk(final SyntaxNode node, final Span receiver, final boolean qualified) {
    if (OTHER_BODIES.contains(node.kind())) {
      return;
    }
    if (receiver != null && "thisExpression".equals(node.kind())) {
      reads.add(read(node.span(), receiver));
    } else if (POSTFIX.equals(node.kind())) {
      chain(node, receiver, qualified);
    } else {
      node.children().forEach(child -> walk(child, receiver, false));
    }
  }

  private void chain(final SyntaxNode node, final Span receiver, final boolean qualified) {
    final List<SyntaxNode> parts = KotlinFlowSyntax.parts(node);
    final SyntaxNode atom = parts.get(0);
    final String name = KotlinCalls.nameOf(atom);
    if (name.isEmpty()) {
      walk(atom, receiver, false);
    } else if (!qualified
        && receiver != null
        && KotlinCalls.startsWithCall(parts.subList(1, parts.size()))
        && implicit(name)
        && noArguments(parts.get(1))) {
      // The read sits on the call's parentheses, so a sanitizer around the call clears it.
      reads.add(read(parts.get(1).span(), receiver));
    }
    String callee = name;
    for (final SyntaxNode operation : parts.subList(1, parts.size())) {
      final SyntaxNode member = KotlinFlowSyntax.child(operation, MEMBER);
      if (member != null) {
        walk(KotlinFlowSyntax.child(operation, POSTFIX), receiver, true);
      } else {
        operation(operation, callee, receiver);
      }
      callee = "";
    }
  }

  private void operation(final SyntaxNode operation, final String callee, final Span receiver) {
    final SyntaxNode suffix = KotlinFlowSyntax.child(operation, CALL_SUFFIX);
    if (suffix == null) {
      operation.children().forEach(child -> walk(child, receiver, false));
      return;
    }
    for (final SyntaxNode part : KotlinFlowSyntax.parts(suffix)) {
      final SyntaxNode lambda =
          ANNOTATED.equals(part.kind()) ? KotlinFlowSyntax.child(part, LAMBDA) : null;
      if (lambda == null) {
        walk(part, receiver, false);
      } else {
        lambda.children().forEach(child -> walk(child, scope(callee, lambda, receiver), false));
      }
    }
  }

  private static Span scope(final String callee, final SyntaxNode lambda, final Span outer) {
    return RECEIVER_FUNCTIONS.contains(callee) ? lambda.span() : outer;
  }

  private static boolean noArguments(final SyntaxNode operation) {
    return KotlinCalls.argumentsOf(KotlinFlowSyntax.child(operation, CALL_SUFFIX)).isEmpty();
  }

  private boolean implicit(final String name) {
    return !Character.isUpperCase(name.charAt(0))
        && !declared.contains(name)
        && !STANDARD.contains(name);
  }

  private static VariableAccess read(final Span span, final Span lambda) {
    return new VariableAccess(THIS, AccessKind.READ, span, lambda);
  }
}
