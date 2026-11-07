package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a call to a JDK method added after Java 8, when the Java release of the project is 8, as
 * Error Prone's Java8ApiChecker does for the common cases. With any other release, or an unknown
 * one, it reports nothing, and so does a call whose facts are unknown.
 */
public final class JavaJdk8ApiCheck implements RuleCheck {

  private static final int RELEASE = 8;
  private static final Map<String, Set<String>> ADDED =
      Map.ofEntries(
          Map.entry("java.util.List", Set.of("of", "copyOf")),
          Map.entry("java.util.Set", Set.of("of", "copyOf")),
          Map.entry("java.util.Map", Set.of("of", "ofEntries", "entry", "copyOf")),
          Map.entry(
              "java.util.Optional",
              Set.of("isEmpty", "ifPresentOrElse", "or", "stream", "orElseThrow")),
          Map.entry(
              "java.lang.String",
              Set.of("isBlank", "strip", "stripLeading", "stripTrailing", "lines", "repeat")),
          Map.entry("java.util.Objects", Set.of("requireNonNullElse", "requireNonNullElseGet")),
          Map.entry("java.nio.file.Files", Set.of("readString", "writeString")),
          Map.entry("java.io.InputStream", Set.of("readAllBytes", "readNBytes", "transferTo")),
          Map.entry(
              "java.util.stream.Stream", Set.of("takeWhile", "dropWhile", "ofNullable", "toList")),
          Map.entry(
              "java.util.stream.Collectors",
              Set.of(
                  "toUnmodifiableList",
                  "toUnmodifiableSet",
                  "toUnmodifiableMap",
                  "filtering",
                  "flatMapping")));

  private final int release;
  private final String message;

  /**
   * Creates the check.
   *
   * @param release the Java release of the project, zero when unknown
   * @param message the message; {@code {value}} is the called method
   */
  public JavaJdk8ApiCheck(final int release, final String message) {
    this.release = CheckSupport.nonNegative(release, "release");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return TypedChecks.CALLS;
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
    final List<RuleMatch> matches = new ArrayList<>();
    if (release == RELEASE) {
      for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("expression"))) {
        types
            .callTarget(visit.node().span())
            .filter(JavaJdk8ApiCheck::added)
            .ifPresent(
                target ->
                    matches.add(
                        CheckSupport.at(
                            visit.node().span(), CheckSupport.fill(message, label(target), ""))));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean added(final CallTarget target) {
    return ADDED.getOrDefault(target.declaringType().name(), Set.of()).contains(target.name());
  }

  private static String label(final CallTarget target) {
    return target.declaringType().name() + "." + target.name();
  }
}
