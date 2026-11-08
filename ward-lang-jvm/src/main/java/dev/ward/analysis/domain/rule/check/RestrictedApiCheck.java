package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Reports a call of a method or constructor annotated {@code @RestrictedApi} from a place its
 * annotation does not allow, as Error Prone's RestrictedApi does. The call is allowed in a file
 * whose path matches {@code allowedOnPath}, and inside a declaration annotated with one of {@code
 * allowlistAnnotations}. A call that {@code allowlistWithWarningAnnotations} allows is a warning
 * for Error Prone, so it is not reported here. A restriction that names {@code allowedPaths} or
 * {@code allowedInTestonlyTargets}, which need the build, is never reported, nor is a call whose
 * target is unknown.
 */
public final class RestrictedApiCheck implements RuleCheck {

  private static final String ANNOTATION = "com.google.errorprone.annotations.RestrictedApi";
  private static final String PREFIX = ANNOTATION + "#";
  private static final String EXPRESSION = "expression";
  private static final Set<String> DECLARATIONS =
      Set.of(
          "classDeclaration",
          "interfaceDeclaration",
          "enumDeclaration",
          "recordDeclaration",
          "methodDeclaration",
          "constructorDeclaration",
          "genericMethodDeclaration",
          "genericConstructorDeclaration");
  private static final FactNeed NEED =
      new FactNeed(
          Set.of(
              EXPRESSION,
              "classDeclaration",
              "interfaceDeclaration",
              "enumDeclaration",
              "recordDeclaration",
              "methodDeclaration",
              "constructorDeclaration",
              "genericMethodDeclaration",
              "genericConstructorDeclaration"),
          Set.of(FactKind.CALL, FactKind.ANNOTATIONS));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call; {@code {value}} is the explanation of the
   *     restriction
   */
  public RestrictedApiCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return NEED;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .filter(visit -> SyntaxWalk.text(visit.node()).endsWith(")"))
        .filter(visit -> restricted(visit, types, unit))
        .map(
            visit ->
                CheckSupport.at(
                    visit.node().span(),
                    CheckSupport.fill(message, explanation(visit.node(), types), "")))
        .toList();
  }

  private static boolean restricted(
      final SyntaxWalk.Visit visit, final UnitTypeFacts types, final SourceUnit unit) {
    final CallTarget target = types.callTarget(visit.node().span()).orElse(null);
    if (target == null || !target.annotations().contains(ANNOTATION)) {
      return false;
    }
    final Map<String, List<String>> values = target.annotationValues();
    return values.getOrDefault(PREFIX + "allowedPaths", List.of()).isEmpty()
        && !values.getOrDefault(PREFIX + "allowedInTestonlyTargets", List.of()).contains("true")
        && !allowedOnPath(values.getOrDefault(PREFIX + "allowedOnPath", List.of()), unit.path())
        && !enclosedBy(visit.ancestors(), allowlisted(values), types);
  }

  private static Set<String> allowlisted(final Map<String, List<String>> values) {
    final Set<String> names = new HashSet<>();
    for (final String element :
        List.of(
            "allowlistAnnotations",
            "whitelistAnnotations",
            "allowlistWithWarningAnnotations",
            "whitelistWithWarningAnnotations")) {
      names.addAll(values.getOrDefault(PREFIX + element, List.of()));
    }
    return names;
  }

  private static boolean allowedOnPath(final List<String> patterns, final String path) {
    final String file = path.substring(path.lastIndexOf('/') + 1);
    for (final String regex : patterns) {
      try {
        if (Pattern.matches(regex, path)
            || Pattern.matches(regex, "/" + path)
            || Pattern.matches(regex, file)) {
          return true;
        }
      } catch (PatternSyntaxException invalid) {
        return true;
      }
    }
    return false;
  }

  private static boolean enclosedBy(
      final List<SyntaxNode> ancestors, final Set<String> names, final UnitTypeFacts types) {
    return !names.isEmpty()
        && ancestors.stream()
            .filter(node -> DECLARATIONS.contains(node.kind()))
            .anyMatch(
                node -> types.at(node.span()).annotations().stream().anyMatch(names::contains));
  }

  private static String explanation(final SyntaxNode call, final UnitTypeFacts types) {
    final CallTarget target = types.callTarget(call.span()).orElseThrow();
    return target
        .annotationValues()
        .getOrDefault(PREFIX + "explanation", List.of(target.name()))
        .stream()
        .findFirst()
        .orElse(target.name());
  }
}
