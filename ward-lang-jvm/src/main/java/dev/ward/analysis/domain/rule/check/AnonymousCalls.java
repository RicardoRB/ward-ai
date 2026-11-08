package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the methods that a file calls or references, as {@code name/arity} and {@code name/*}. */
final class AnonymousCalls {

  private static final String METHOD_CALL = "methodCall";
  private static final Pattern REFERENCE = Pattern.compile("::([A-Za-z_$][A-Za-z0-9_$]*)");

  private AnonymousCalls() {}

  /** Returns {@code name/arity} for each call and {@code name/*} for each method reference. */
  static Set<String> of(final SyntaxNode root) {
    final Set<String> out = new HashSet<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      if (METHOD_CALL.equals(node.kind())) {
        out.add(NullOperands.methodName(node) + "/" + NullFlow.arguments(node).size());
      }
    }
    final Matcher references = REFERENCE.matcher(SyntaxWalk.text(root));
    while (references.find()) {
      out.add(references.group(1) + "/*");
    }
    return out;
  }
}
