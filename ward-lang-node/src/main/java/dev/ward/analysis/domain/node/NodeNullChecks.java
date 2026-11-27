package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Marks the null check of a lookup result for the analysis: past a branch that exits when {@code
 * x == null} or {@code !x}, where {@code x = await f(k)} assigned the only value of {@code x}, a
 * site named {@value #NULL_CHECKED} (the qualifier is {@code k}, the annotations {@code f} and
 * {@code x}) says that {@code k} is a member if {@code f} returns a non-null value only for
 * members.
 */
final class NodeNullChecks {
  /** The name of the site that marks a null check of the result of a call. */
  static final String NULL_CHECKED = "<null-checked>";

  /**
   * The name of the site that marks the check that {@code x.list.length} is zero, for {@code x =
   * f(k)}: the qualifier is {@code k}, the annotations {@code f}, {@code x} and {@code list}.
   */
  static final String LENGTH_CHECKED = "<length-checked>";

  private static final String NAME = "([A-Za-z_$][\\w$]*)";
  private static final Pattern CALL_ASSIGN =
      Pattern.compile(
          "^(?:(?:const|let|var))?" + NAME + "(?::[^=]+)?=await" + NAME + "\\(" + NAME + "\\);?$"
      );
  private static final Pattern NULL_TEST =
      Pattern.compile("^(?:" + NAME + "==null|!" + NAME + ")$");
  private static final Pattern CALL_ASSIGN_ANY =
      Pattern.compile(
          "^(?:(?:const|let|var))?" + NAME + "(?::[^=]+)?=(?:await)?" + NAME + "\\(" + NAME + "\\);?$"
      );
  private static final Pattern EMPTY_TEST =
      Pattern.compile(
          "^(?:" + NAME + "\\." + NAME + "\\.length===?0|!" + NAME + "\\." + NAME
              + "\\.length)$"
      );
  private static final int NAME_GROUP = 1;
  private static final int SECOND_GROUP = 2;
  private static final int KEY_GROUP = 3;

  private final ControlFlowBuilder flow;
  private final Map<String, List<String>> lookups = new HashMap<>();
  private final Map<String, List<String>> calls = new HashMap<>();

  NodeNullChecks(final ControlFlowBuilder flow) {
    this.flow = flow;
  }

  /** Notes {@code x = await f(k)}, which a later null check of {@code x} can turn into a fact. */
  void assigned(final String text) {
    final Matcher call = CALL_ASSIGN.matcher(text);
    if (call.matches()) {
      lookups.put(call.group(NAME_GROUP), List.of(call.group(SECOND_GROUP), call.group(KEY_GROUP)));
    }
    final Matcher any = CALL_ASSIGN_ANY.matcher(text);
    if (any.matches()) {
      calls.put(any.group(NAME_GROUP), List.of(any.group(SECOND_GROUP), any.group(KEY_GROUP)));
    }
  }

  /** Marks the check of each alternative of the condition that tests a noted result for null. */
  void checked(final List<SyntaxNode> alternatives, final Span span) {
    alternatives.stream()
        .map(part -> NULL_TEST.matcher(NodeFlowSyntax.text(part)))
        .filter(Matcher::matches)
        .map(
            match -> match.group(NAME_GROUP) == null ? match.group(SECOND_GROUP) : match.group(
                NAME_GROUP
            )
        )
        .filter(lookups::containsKey)
        .forEach(name -> mark(name, span));
  }

  private void mark(final String name, final Span span) {
    final List<String> call = lookups.get(name);
    flow.record(
        List.of(),
        List.of(
            new FlowSite(
                FlowSiteKind.CALL,
                NULL_CHECKED,
                call.get(1),
                Optional.empty(),
                List.of(call.get(0), name),
                List.of(),
                span
            )
        )
    );
  }

  /** Marks the checks that the condition failed, at the start of its {@code else} branch. */
  void otherwise(final SyntaxNode condition) {
    emptied(NodeGuards.alternatives(condition), NodeGuards.after(condition.span()));
  }

  /**
   * Marks the check of each alternative of the condition that tests the list of a noted call result
   * for emptiness, at the span that starts the code where every alternative failed.
   */
  void emptied(final List<SyntaxNode> alternatives, final Span span) {
    for (final SyntaxNode part : alternatives) {
      final Matcher test = EMPTY_TEST.matcher(NodeFlowSyntax.text(part));
      if (test.matches()) {
        final boolean negated = test.group(NAME_GROUP) == null;
        final String result = negated ? test.group(KEY_GROUP) : test.group(NAME_GROUP);
        final String list = negated ? test.group(KEY_GROUP + 1) : test.group(SECOND_GROUP);
        if (calls.containsKey(result)) {
          markEmpty(result, list, span);
        }
      }
    }
  }

  private void markEmpty(final String result, final String list, final Span span) {
    final List<String> call = calls.get(result);
    flow.record(
        List.of(),
        List.of(
            new FlowSite(
                FlowSiteKind.CALL,
                LENGTH_CHECKED,
                call.get(1),
                Optional.empty(),
                List.of(call.get(0), result, list),
                List.of(),
                span
            )
        )
    );
  }
}
