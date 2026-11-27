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

  private static final String NAME = "([A-Za-z_$][\\w$]*)";
  private static final Pattern CALL_ASSIGN =
      Pattern.compile(
          "^(?:(?:const|let|var))?" + NAME + "(?::[^=]+)?=await" + NAME + "\\(" + NAME + "\\);?$"
      );
  private static final Pattern NULL_TEST =
      Pattern.compile("^(?:" + NAME + "==null|!" + NAME + ")$");
  private static final int NAME_GROUP = 1;
  private static final int SECOND_GROUP = 2;
  private static final int KEY_GROUP = 3;

  private final ControlFlowBuilder flow;
  private final Map<String, List<String>> lookups = new HashMap<>();

  NodeNullChecks(final ControlFlowBuilder flow) {
    this.flow = flow;
  }

  /** Notes {@code x = await f(k)}, which a later null check of {@code x} can turn into a fact. */
  void assigned(final String text) {
    final Matcher call = CALL_ASSIGN.matcher(text);
    if (call.matches()) {
      lookups.put(call.group(NAME_GROUP), List.of(call.group(SECOND_GROUP), call.group(KEY_GROUP)));
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
}
