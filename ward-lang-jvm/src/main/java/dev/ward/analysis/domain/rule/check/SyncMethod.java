package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/** A method with a body, as the inconsistent-synchronization check reads it. */
record SyncMethod(
    SyntaxNode node, String name, int arity, boolean isPublic, boolean isStatic, boolean sync) {

  private static final String METHOD = "methodDeclaration";
  private static final Set<String> SPECIAL =
      Set.of(
          "toString",
          "init",
          "initialize",
          "dispose",
          "writeObject",
          "_jspInit",
          "_jspDestroy",
          "this");

  /** Tells whether calling the method takes the lock on {@code this}. */
  boolean locksThis() {
    return sync && !isStatic;
  }

  /** Tells whether the method has a name that the detector treats as a constructor. */
  static boolean special(final String name) {
    return SPECIAL.contains(name);
  }

  static List<SyncMethod> in(final List<JavaNodes.Member> members) {
    return members.stream()
        .filter(m -> METHOD.equals(m.node().kind()))
        .filter(m -> JavaNodes.kid(m.node(), "methodBody").isPresent())
        .map(
            m ->
                new SyncMethod(
                    m.node(),
                    JavaNodes.name(m.node()),
                    arity(m.node()),
                    m.has("public"),
                    m.has("static"),
                    m.has("synchronized")))
        .toList();
  }

  private static int arity(final SyntaxNode method) {
    return (int)
        SyntaxWalk.descendants(JavaNodes.kid(method, "formalParameters").orElse(method)).stream()
            .filter(
                n -> "formalParameter".equals(n.kind()) || "lastFormalParameter".equals(n.kind()))
            .count();
  }

  static boolean distinct(final List<SyncMethod> methods) {
    return methods.stream().map(m -> m.name() + "/" + m.arity()).distinct().count()
        == methods.size();
  }
}
