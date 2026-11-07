package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reports a JUnitParams {@code @Parameters(method = "...")} that names a method the class does not
 * declare, as Error Prone's JUnitParameterMethodNotFound does for methods of the same class.
 */
public final class JavaJUnitParameterMethodCheck implements RuleCheck {

  private static final String ANNOTATION = "annotation";
  private static final Pattern PARAMETERS =
      Pattern.compile("^@(?:\\w+\\.)*Parameters\\(.*?method=\"([^\"]*)\"");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the missing method name
   */
  public JavaJUnitParameterMethodCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("classDeclaration"))) {
      final List<JavaNodes.Member> members = JavaNodes.members(visit.node());
      final Set<String> methods =
          members.stream()
              .filter(member -> "methodDeclaration".equals(member.node().kind()))
              .map(member -> JavaNodes.name(member.node()))
              .collect(Collectors.toUnmodifiableSet());
      members.forEach(member -> check(member, methods, matches));
    }
    return List.copyOf(matches);
  }

  private void check(
      final JavaNodes.Member member, final Set<String> methods, final List<RuleMatch> matches) {
    for (final SyntaxNode node : SyntaxWalk.descendants(member.holder())) {
      if (ANNOTATION.equals(node.kind())) {
        final Matcher found = PARAMETERS.matcher(SyntaxWalk.text(node));
        if (found.find()) {
          Arrays.stream(found.group(1).split(","))
              .map(String::strip)
              .filter(name -> !name.isEmpty() && !methods.contains(name))
              .forEach(
                  name ->
                      matches.add(
                          CheckSupport.at(node.span(), CheckSupport.fill(message, name, ""))));
        }
      }
    }
  }
}
