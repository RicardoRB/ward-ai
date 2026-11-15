package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Reads the JUnit version of a file and the parameters and assertions of a test method. */
final class JUnitMethods {
  private static final String IDENTIFIER = "identifier";
  private static final String METHOD_CALL = "methodCall";
  private static final Pattern JUNIT4_IMPORT =
      Pattern.compile(
          "^import(?:static)?(?:org\\.junit\\.(?!jupiter|platform)|junit\\.framework\\.).*"
      );
  private static final String JUPITER_IMPORT = "org.junit.jupiter";

  private JUnitMethods() {}

  /**
   * Returns whether the file is a JUnit 4 (or 3) test file: it imports {@code org.junit} types
   * other than Jupiter and platform ones, or {@code junit.framework}, and imports nothing of {@code
   * org.junit.jupiter}.
   */
  static boolean usesJunit4(final SyntaxNode root) {
    final List<String> imports =
        SyntaxWalk.visits(root, Set.of("importDeclaration")).stream()
            .map(visit -> SyntaxWalk.text(visit.node()))
            .toList();
    return imports.stream().noneMatch(text -> text.contains(JUPITER_IMPORT))
        && imports.stream().anyMatch(text -> JUNIT4_IMPORT.matcher(text).matches());
  }

  /** Returns how many assertion calls a method body has: calls whose name starts with assert. */
  static long assertionCount(final SyntaxNode method) {
    return SyntaxWalk.descendants(method).stream()
        .filter(node -> METHOD_CALL.equals(node.kind()))
        .filter(
            node ->
                node.children().stream()
                    .anyMatch(
                        c ->
                            IDENTIFIER.equals(c.kind()) && SyntaxWalk.text(c).startsWith("assert")
                    )
        )
        .count();
  }

  /**
   * Returns whether a method calls what PMD takes for an assertion: a method whose name starts with
   * {@code assert}, {@code check} or {@code verify}, or is {@code fail}.
   */
  static boolean callsProbableAssertion(final SyntaxNode method) {
    return SyntaxWalk.descendants(method).stream()
        .filter(node -> METHOD_CALL.equals(node.kind()))
        .flatMap(node -> node.children().stream())
        .filter(c -> IDENTIFIER.equals(c.kind()))
        .map(SyntaxWalk::text)
        .anyMatch(
            name ->
                name.startsWith("assert")
                    || name.startsWith("check")
                    || name.startsWith("verify")
                    || "fail".equals(name)
        );
  }

  /** Returns whether a method has no formal parameters. */
  static boolean hasNoParameters(final SyntaxNode method) {
    return method.children().stream()
        .filter(child -> "formalParameters".equals(child.kind()))
        .allMatch(
            child -> child.children().stream().noneMatch(c -> "formalParameter".equals(c.kind()))
        );
  }
}
