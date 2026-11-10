package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a class whose {@code __slots__} repeat a slot of its base class defined in the same file,
 * like Pylint W0244.
 */
public final class RedefinedSlotsInSubclassCheck implements RuleCheck {

  private static final Set<String> CLASSES = Set.of("classdef");
  private static final Set<String> ASSIGNMENTS = Set.of("expr_stmt");
  private static final Pattern CLASS_NAME = Pattern.compile("^class(\\w+)[:(]");
  private static final Pattern BASE = Pattern.compile("^class\\w+\\((\\w+)\\)");
  private static final Pattern SLOT = Pattern.compile("['\"]([^'\"]+)['\"]");
  private static final String SLOTS_ASSIGNMENT = "__slots__=";

  private final String message;

  /** Creates the check with the message of each finding. */
  public RedefinedSlotsInSubclassCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Map<String, Set<String>> slots = new HashMap<>();
    final List<SyntaxWalk.Visit> classes = SyntaxWalk.visits(root, CLASSES);
    for (final SyntaxWalk.Visit visit : classes) {
      final Matcher name = CLASS_NAME.matcher(SyntaxWalk.text(visit.node()));
      if (name.find()) {
        slots.putIfAbsent(name.group(1), ownSlots(visit.node()));
      }
    }
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : classes) {
      if (redefines(visit.node(), slots)) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private static boolean redefines(final SyntaxNode cls, final Map<String, Set<String>> slots) {
    final Matcher base = BASE.matcher(SyntaxWalk.text(cls));
    if (!base.find() || !slots.containsKey(base.group(1))) {
      return false;
    }
    final Set<String> inherited = new HashSet<>(slots.get(base.group(1)));
    inherited.retainAll(ownSlots(cls));
    return !inherited.isEmpty();
  }

  /** The slot names that {@code cls} declares in its own body. */
  private static Set<String> ownSlots(final SyntaxNode cls) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxNode assignment :
        FunctionBodies.own(cls, ASSIGNMENTS, FunctionBodies.SCOPES)) {
      final String text = SyntaxWalk.text(assignment);
      if (text.startsWith(SLOTS_ASSIGNMENT)) {
        final Matcher slot = SLOT.matcher(text);
        while (slot.find()) {
          names.add(slot.group(1));
        }
      }
    }
    return names;
  }
}
