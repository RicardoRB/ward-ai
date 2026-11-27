package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Marks the returns of a body for the analysis of its callers. A body with a {@code return} inside
 * {@code if (C.has(p))} marks each of its returns with a site named {@value #RETURNED}: {@code
 * member} (the qualifier is {@code p}, the annotations {@code member} and {@code C}), {@code null}
 * for a bare return or one of {@code null} and {@code undefined}, {@code collection} for {@code
 * return name} of a collection only the file writes to, and {@code other}. The marks are recorded
 * only when one of them is a fact, so other bodies stay as they are.
 */
final class NodeReturnMarks {
  /** The name of the site that marks a return of a body. */
  static final String RETURNED = "<returned>";

  private static final String NAME = "([A-Za-z_$][\\w$]*)";
  private static final Pattern MEMBER_TEST = Pattern.compile(
      "^" + NAME + "\\.has\\(" + NAME + "\\)$"
  );
  private static final Pattern RETURN_NAME = Pattern.compile("^return" + NAME + ";?$");
  private static final Pattern RETURN_NULL = Pattern.compile("^return(?:null|undefined)?;?$");
  private static final String OTHER = "other";
  private static final String COLLECTION = "collection";
  private static final String MEMBER = "member";
  private static final String NULL = "null";
  private static final int NAME_GROUP = 1;
  private static final int SECOND_GROUP = 2;

  private final ControlFlowBuilder flow;
  private final Set<String> collections;
  private final Deque<Optional<List<String>>> members = new ArrayDeque<>();
  private final List<FlowSite> returns = new ArrayList<>();
  private final NodeListings listings;
  private SyntaxNode function;
  private List<String> parameters = List.of();
  private boolean returnsMembers;

  NodeReturnMarks(
      final ControlFlowBuilder flow,
      final Set<String> collections,
      final Map<String,
      List<Integer>> caches
  ) {
    this.flow = flow;
    this.listings = new NodeListings(flow, caches);
    this.collections = Set.copyOf(collections);
  }

  /** Records the parameters of the body and notes them for the listing mark of {@link #finish()}. */
  void entry(
      final SyntaxNode body,
      final List<VariableAccess> entries,
      final List<FlowSite> sites
  ) {
    flow.record(entries, sites);
    function = body;
    parameters = entries.stream().map(VariableAccess::name).toList();
  }

  /** Enters an {@code if}: the returns inside it are members when the condition is {@code C.has(p)}. */
  void enter(final SyntaxNode condition) {
    members.push(
        NodeGuards.conjuncts(condition).stream()
            .map(part -> MEMBER_TEST.matcher(NodeFlowSyntax.text(part)))
            .filter(Matcher::matches)
            .map(match -> List.of(match.group(NAME_GROUP), match.group(SECOND_GROUP)))
            .findFirst()
    );
  }

  /** Leaves the branch that {@link #enter(SyntaxNode)} entered. */
  void leave() {
    members.pop();
  }

  /** Marks a return statement. */
  void returned(final SyntaxNode statement) {
    final String text = NodeFlowSyntax.text(statement);
    final Matcher name = RETURN_NAME.matcher(text);
    final Optional<List<String>> member = members.stream().flatMap(Optional::stream).findFirst();
    if (name.matches() && collections.contains(name.group(NAME_GROUP))) {
      returnsMembers = true;
      returns.add(mark(name.group(NAME_GROUP), statement, COLLECTION));
    } else if (RETURN_NULL.matcher(text).matches()) {
      returns.add(mark("", statement, NULL));
    } else if (member.isPresent()) {
      returnsMembers = true;
      returns.add(mark(member.get().get(1), statement, MEMBER, member.get().get(0)));
    } else {
      returns.add(mark("", statement, OTHER));
    }
  }

  /**
   * Records the marks in the entry block, which the exit always keeps, when one of them is a fact.
   */
  void finish() {
    listings.mark(function, parameters);
    if (returnsMembers) {
      final int last = flow.current();
      flow.moveTo(0);
      flow.record(List.of(), returns);
      flow.moveTo(last);
    }
  }

  private static FlowSite mark(
      final String qualifier,
      final SyntaxNode statement,
      final String... annotations
  ) {
    return new FlowSite(
        FlowSiteKind.CALL,
        RETURNED,
        qualifier,
        Optional.empty(),
        List.of(annotations),
        List.of(),
        statement.span()
    );
  }
}
