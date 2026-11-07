package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a field of a class annotated with Error Prone's {@code @Immutable} that is not final, is
 * an array, or has a well-known mutable type, as Error Prone's Immutable does for the plain case.
 * The annotation counts only when the file imports {@code
 * com.google.errorprone.annotations.Immutable}.
 */
public final class JavaImmutableCheck implements RuleCheck {

  private static final String ANNOTATION = "com.google.errorprone.annotations.Immutable";
  private static final Set<String> UTIL_TYPES =
      Set.of(
          "List",
          "ArrayList",
          "LinkedList",
          "Map",
          "HashMap",
          "TreeMap",
          "LinkedHashMap",
          "Set",
          "HashSet",
          "TreeSet",
          "LinkedHashSet",
          "Collection",
          "Deque",
          "ArrayDeque",
          "Queue",
          "Date");
  private static final Set<String> LANG_TYPES = Set.of("StringBuilder", "StringBuffer");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the field name
   */
  public JavaImmutableCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Set<String> imports = JavaAnnotations.imports(root);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("classDeclaration"))) {
      if (immutable(visit, imports)) {
        JavaNodes.members(visit.node()).stream()
            .filter(member -> "fieldDeclaration".equals(member.node().kind()))
            .filter(member -> !member.has("static"))
            .filter(member -> mutable(member, imports))
            .forEach(member -> matches.add(report(member.node())));
      }
    }
    return List.copyOf(matches);
  }

  private RuleMatch report(final SyntaxNode field) {
    final String name =
        SyntaxWalk.descendants(field).stream()
            .filter(node -> "variableDeclaratorId".equals(node.kind()))
            .findFirst()
            .map(SyntaxWalk::text)
            .orElse("");
    return CheckSupport.at(field.span(), CheckSupport.fill(message, name, ""));
  }

  private static boolean immutable(final SyntaxWalk.Visit visit, final Set<String> imports) {
    return JavaNodes.modifiers(visit).stream()
        .anyMatch(
            text ->
                text.startsWith("@" + ANNOTATION)
                    || text.startsWith("@Immutable") && imports.contains(ANNOTATION));
  }

  private static boolean mutable(final JavaNodes.Member member, final Set<String> imports) {
    if (!member.has("final")) {
      return true;
    }
    final SyntaxNode type = JavaNodes.kid(member.node(), "typeType").orElse(null);
    if (type == null) {
      return false;
    }
    final String text = SyntaxWalk.text(type);
    final String simple = text.replaceAll("<.*", "");
    return text.endsWith("]")
        || UTIL_TYPES.contains(simple)
            && JavaAnnotations.resolves(imports, simple, "java.util." + simple)
        || LANG_TYPES.contains(simple)
        || simple.startsWith("java.util.")
            && UTIL_TYPES.contains(simple.substring("java.util.".length()));
  }
}
