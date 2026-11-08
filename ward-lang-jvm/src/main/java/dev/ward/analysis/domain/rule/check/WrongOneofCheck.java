package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.FieldRef;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reports a read of another case of a protocol buffer {@code oneof} inside a {@code case} of a
 * {@code switch} on {@code message.getXxxCase()}, as Error Prone's WrongOneof does: that field is
 * known not to be set. The cases are the constants of the type of the selector; a selector whose
 * constants are unknown is never reported.
 */
public final class WrongOneofCheck extends FactsCheck {

  private static final Pattern SELECTOR = Pattern.compile("^(.+)\\.get\\w+Case\\(\\)$");
  private static final Pattern CAMEL = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");
  private static final String STATEMENT = "statement";
  private static final String SWITCH = "'switch'";
  private static final String GROUP = "switchBlockStatementGroup";
  private static final String LABEL = "switchLabel";
  private static final String EXPRESSION = "expression";
  private static final FactNeed NEED =
      new FactNeed(Set.of(STATEMENT), Set.of(FactKind.TYPE, FactKind.MEMBERS));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the read; {@code {value}} is the field
   */
  public WrongOneofCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    return SyntaxWalk.visits(root, Set.of(STATEMENT)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(node -> node.children().stream().anyMatch(child -> SWITCH.equals(child.kind())))
        .flatMap(node -> reportsOf(node, types).stream())
        .toList();
  }

  private List<RuleMatch> reportsOf(final SyntaxNode node, final UnitTypeFacts types) {
    final Optional<SyntaxNode> selector =
        SyntaxWalk.descendants(node).stream().filter(n -> EXPRESSION.equals(n.kind())).findFirst();
    final Matcher receiver = SELECTOR.matcher(selector.map(SyntaxWalk::text).orElse(""));
    if (selector.isEmpty() || !receiver.matches()) {
      return List.of();
    }
    final Set<String> cases =
        types.membersOf(selector.get().span()).map(TypeMembers::fields).stream()
            .flatMap(List::stream)
            .map(FieldRef::name)
            .collect(Collectors.toSet());
    final Pattern read =
        Pattern.compile("^" + Pattern.quote(receiver.group(1)) + "\\.(?:get|has)(\\w+)\\(\\)$");
    return node.children().stream()
        .filter(group -> GROUP.equals(group.kind()))
        .flatMap(group -> readsIn(group, cases, read).stream())
        .toList();
  }

  private List<RuleMatch> readsIn(
      final SyntaxNode group, final Set<String> cases, final Pattern read) {
    final Set<String> labels =
        group.children().stream()
            .filter(child -> LABEL.equals(child.kind()))
            .map(label -> SyntaxWalk.text(label).replaceFirst("^case", "").replace(":", ""))
            .collect(Collectors.toSet());
    if (labels.contains("default")) {
      return List.of();
    }
    return SyntaxWalk.visits(group, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(expression -> otherCase(expression, read, cases, labels).stream())
        .toList();
  }

  private Optional<RuleMatch> otherCase(
      final SyntaxNode expression,
      final Pattern read,
      final Set<String> cases,
      final Set<String> labels) {
    final Matcher matcher = read.matcher(SyntaxWalk.text(expression));
    if (!matcher.matches() || labels.isEmpty()) {
      return Optional.empty();
    }
    final String constant =
        CAMEL.matcher(matcher.group(1)).replaceAll("_").toUpperCase(Locale.ROOT);
    return cases.contains(constant) && !labels.contains(constant)
        ? Optional.of(
            CheckSupport.at(expression.span(), CheckSupport.fill(message, matcher.group(1), "")))
        : Optional.empty();
  }
}
