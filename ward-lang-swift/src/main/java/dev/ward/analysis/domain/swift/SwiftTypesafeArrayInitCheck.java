package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports an identity {@code map} ({@code map { $0 }}, {@code map { x in x }}) on a value that is
 * a sequence, where {@code Array(sequence)} is the type-safe spelling, like SwiftLint's {@code
 * typesafe_array_init}. The {@code map} is reported. Simplification: SwiftLint asks the compiler for
 * the resolved {@code map}; here a receiver is a sequence when it is an array literal, a constructor
 * of a sequence type declared in the file, or a name whose declared type in the file is an array,
 * set, or generic parameter constrained to a sequence or collection.
 */
public final class SwiftTypesafeArrayInitCheck implements RuleCheck {

  private static final String CALL = "postfix_expression";
  private static final String MAP = "map";
  private static final String DOT = ".";
  private static final Pattern IDENTITY_MAP =
      Pattern.compile("^(.+)\\.map\\(?\\{(?:(\\w+)in)?(?:return)?(\\$0|\\w+)\\}\\)?$");
  private static final Pattern ARRAY_LITERAL = Pattern.compile("^\\[.*\\]$");
  private static final Pattern CONSTRUCTOR = Pattern.compile("^(\\w+)\\(\\)$");
  private static final Pattern NAME = Pattern.compile("^\\w+$");
  private static final String SEQUENCE_TYPES = "Sequence|Collection";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftTypesafeArrayInitCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final String file = SwiftTrees.compact(root);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode call : SwiftTrees.descendants(root, CALL)) {
      final Matcher identity = IDENTITY_MAP.matcher(SwiftTrees.compact(call));
      if (identity.matches() && isIdentity(identity) && isSequence(identity.group(1), file)) {
        mapOf(call).ifPresent(map -> matches.add(SwiftTrees.at(map, message)));
      }
    }
    return List.copyOf(matches);
  }

  /** The closure returns its own parameter: {@code $0}, {@code x in x}, or {@code return $0}. */
  private static boolean isIdentity(final Matcher identity) {
    final String parameter = identity.group(2) != null ? identity.group(2) : "$0";
    return parameter.equals(identity.group(3));
  }

  private static boolean isSequence(final String receiver, final String file) {
    if (ARRAY_LITERAL.matcher(receiver).matches()) {
      return true;
    }
    final Matcher constructor = CONSTRUCTOR.matcher(receiver);
    if (constructor.matches()) {
      return file.matches(".*(?:struct|class|enum)" + constructor.group(1) + ":[^{]*(?:" + SEQUENCE_TYPES + ").*");
    }
    return NAME.matcher(receiver).matches() && hasSequenceType(receiver, file);
  }

  /** A name declared with an array or set type, or with a generic type constrained to a sequence. */
  private static boolean hasSequenceType(final String name, final String file) {
    if (file.matches(".*\\b" + name + ":(?:\\[|Array<|Set<).*")) {
      return true;
    }
    final Matcher generic = Pattern.compile("\\b" + name + ":(\\w+)\\b").matcher(file);
    while (generic.find()) {
      final String type = generic.group(1);
      if (file.matches(".*<[^>]*\\b" + type + ":[^>,]*(?:" + SEQUENCE_TYPES + ").*")) {
        return true;
      }
    }
    return false;
  }

  /** The {@code map} identifier that follows the dot of the call. */
  private static Optional<SyntaxNode> mapOf(final SyntaxNode call) {
    final List<SyntaxNode> leaves = SwiftTrees.leaves(call);
    for (int i = 1; i < leaves.size(); i++) {
      if (MAP.equals(leaves.get(i).fields().get("text")) && DOT.equals(leaves.get(i - 1).fields().get("text"))) {
        return Optional.of(leaves.get(i));
      }
    }
    return Optional.empty();
  }
}
