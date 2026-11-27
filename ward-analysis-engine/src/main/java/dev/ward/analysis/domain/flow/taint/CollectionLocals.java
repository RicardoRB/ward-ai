package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Finds the locals that hold a collection a getter gave and keep no reference to it: a local that a
 * call initializes as a whole ({@code const x = await getter()}), assigned once, captured by no
 * nested body, and read only to call {@code has}, {@code get}, {@code keys}, {@code values},
 * {@code entries} or {@code set} on, to read {@code .size}, or to test {@code instanceof}.
 */
final class CollectionLocals {
  private static final int TOKENS_AFTER = 3;
  private static final int CALL_TOKENS = 3;
  private static final Set<String> USES = Set.of("has", "get", "keys", "values", "entries", "set");
  private static final String INSTANCEOF = "instanceof";
  private static final String DOT = ".";
  private static final String SIZE = "size";
  private static final String OPEN = "(";
  private static final String TEXT = "text";
  private static final Pattern INITIALIZER =
      Pattern.compile(
          "^(?:const|let|var)([A-Za-z_$][\\w$]*)(?::[^=]+)?=(?:await)?(?:[\\w$]+\\.)*([\\w$]+)\\(\\);?$"
      );

  private CollectionLocals() {}

  /** The local that the call initializes as a whole, when it keeps no reference to the value. */
  static Optional<String> initialized(final ControlFlowGraph graph, final FlowSite call) {
    for (final BasicBlock block : graph.blocks()) {
      final Optional<SyntaxNode> node = TaintSpans.nodeContaining(block, call.span());
      if (node.isPresent()) {
        final Matcher statement = INITIALIZER.matcher(text(node.get()));
        if (statement.matches()
            && statement.group(2).equals(call.name())
            && closedUses(graph, statement.group(1))) {
          return Optional.of(statement.group(1));
        }
      }
    }
    return Optional.empty();
  }

  private static boolean closedUses(final ControlFlowGraph graph, final String name) {
    final List<VariableAccess> accesses =
        graph.blocks().stream()
            .flatMap(block -> block.accesses().stream())
            .filter(access -> access.name().equals(name))
            .toList();
    return MemberLookups.assignments(graph, name) == 1
        && accesses.stream().noneMatch(access -> access.kind() == AccessKind.CAPTURE)
        && accesses.stream()
            .filter(access -> access.kind() == AccessKind.READ)
            .allMatch(read -> plainUse(graph, read));
  }

  private static boolean plainUse(final ControlFlowGraph graph, final VariableAccess read) {
    final List<String> after = following(graph, read);
    if (after.isEmpty()) {
      return false;
    }
    if (INSTANCEOF.equals(after.get(0))) {
      return true;
    }
    return after.size() >= CALL_TOKENS
        && DOT.equals(after.get(0))
        && (SIZE.equals(after.get(1)) || USES.contains(after.get(1)) && OPEN.equals(after.get(2)));
  }

  /** The texts of the tokens that follow the read in its statement, up to three. */
  private static List<String> following(final ControlFlowGraph graph, final VariableAccess read) {
    for (final BasicBlock block : graph.blocks()) {
      final Optional<SyntaxNode> node = TaintSpans.nodeContaining(block, read.span());
      if (node.isPresent()) {
        final List<SyntaxNode> leaves = leavesOf(node.get());
        for (int index = 0; index < leaves.size(); index++) {
          if (leaves.get(index).span().equals(read.span())) {
            return leaves.subList(index + 1, Math.min(leaves.size(), index + TOKENS_AFTER + 1))
                .stream()
                .map(leaf -> leaf.fields().getOrDefault(TEXT, ""))
                .toList();
          }
        }
      }
    }
    return List.of();
  }

  private static List<SyntaxNode> leavesOf(final SyntaxNode node) {
    final List<SyntaxNode> found = new ArrayList<>();
    collect(node, found);
    return found;
  }

  private static void collect(final SyntaxNode node, final List<SyntaxNode> found) {
    if (node.children().isEmpty()) {
      found.add(node);
    }
    node.children().forEach(child -> collect(child, found));
  }

  private static String text(final SyntaxNode node) {
    if (node.children().isEmpty()) {
      return node.fields().getOrDefault(TEXT, "");
    }
    return node.children().stream().map(CollectionLocals::text).collect(Collectors.joining());
  }
}
