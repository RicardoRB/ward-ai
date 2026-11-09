package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reports a generic parameter whose name is not an uppercase-initial alphanumeric identifier of at
 * most twenty characters, like SwiftLint's {@code generic_type_name} with its default settings.
 * Value generic parameters ({@code let N: Int}) are not type names and are skipped.
 */
public final class SwiftGenericTypeNameCheck implements RuleCheck {

  private static final String PARAMETER = "generic_parameter";
  private static final String IDENTIFIER = "declaration_identifier";
  private static final String LET = "let";
  private static final Pattern TYPE_NAME = Pattern.compile("[A-Z][A-Za-z0-9]{0,19}");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftGenericTypeNameCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode parameter : SwiftTrees.descendants(root, PARAMETER)) {
      nameOf(parameter)
          .filter(name -> !TYPE_NAME.matcher(SwiftTrees.compact(name)).matches())
          .filter(name -> !SwiftTrees.hasToken(parameter, LET))
          .ifPresent(name -> matches.add(SwiftTrees.at(name, message)));
    }
    return List.copyOf(matches);
  }

  private static Optional<SyntaxNode> nameOf(final SyntaxNode parameter) {
    return SwiftTrees.descendants(parameter, IDENTIFIER).stream().findFirst();
  }
}
