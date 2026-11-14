package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A method or function of the analysed file that a call in the same file can name: its name, the
 * simple name of its owner, its arity and its graph. Java bodies are named {@code
 * Type#name(arity)}; Node bodies are named by the function name alone, and their arity is the
 * number of parameters. Two bodies with one name and arity, such as overloads in two classes,
 * cannot be told apart by a call site, so neither is a local method.
 *
 * @param name the method name
 * @param owner the simple name of the declaring type, or empty
 * @param arity the number of parameters
 * @param graph the graph of the body
 * @param external whether the body is in another file, so only a call that names the owner can
 *     reach it
 */
record LocalMethod(String name, String owner, int arity, ControlFlowGraph graph, boolean external) {

  private static final Pattern BODY =
      Pattern.compile("^(?:(?<scope>.*)#)?(?<name>[\\w$]+)(?:\\((?<arity>\\d+)\\))?$");
  private static final String THIS = "this";
  private static final String MARK = "ward:tainted-parameter";

  /** The marker that a seeded parameter carries as its annotation. */
  static String mark() {
    return MARK;
  }

  /** Indexes the graphs of a file by name and arity, leaving out the ambiguous ones. */
  static Map<String, LocalMethod> index(final List<ControlFlowGraph> graphs) {
    final Map<String, LocalMethod> found = new HashMap<>();
    final Set<String> ambiguous = new HashSet<>();
    for (final ControlFlowGraph graph : graphs) {
      final Optional<LocalMethod> method = parse(graph);
      if (method.isPresent() && found.putIfAbsent(method.get().key(), method.get()) != null) {
        ambiguous.add(method.get().key());
      }
    }
    ambiguous.forEach(found::remove);
    return found;
  }

  /**
   * Indexes the graphs of other files by owner, name and arity. A body with no owner cannot be
   * named by a call in another file, and two bodies with one owner, name and arity cannot be told
   * apart, so neither is indexed.
   */
  static Map<String, LocalMethod> indexExternal(final List<ControlFlowGraph> graphs) {
    return graphs.stream()
        .map(LocalMethod::parseExternal)
        .flatMap(Optional::stream)
        .collect(Collectors.groupingBy(LocalMethod::key))
        .entrySet()
        .stream()
        .filter(entry -> entry.getValue().size() == 1)
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get(0)));
  }

  /** The {@code Owner#name/arity} keys of the bodies that have an owner. */
  static Set<String> owned(final List<ControlFlowGraph> graphs) {
    return graphs.stream()
        .map(LocalMethod::parse)
        .flatMap(Optional::stream)
        .filter(method -> !method.owner().isEmpty())
        .map(method -> method.owner() + "#" + key(method.name(), method.arity()))
        .collect(Collectors.toSet());
  }

  /** Returns the method with another graph for its body. */
  LocalMethod withGraph(final ControlFlowGraph replaced) {
    return new LocalMethod(name, owner, arity, replaced, external);
  }

  private static Optional<LocalMethod> parseExternal(final ControlFlowGraph graph) {
    return parse(graph)
        .filter(method -> !method.owner().isEmpty())
        .map(method -> new LocalMethod(method.name, method.owner, method.arity, graph, true));
  }

  private static Optional<LocalMethod> parse(final ControlFlowGraph graph) {
    final Matcher body = BODY.matcher(graph.body());
    if (!body.matches()) {
      return Optional.empty();
    }
    final String scope = body.group("scope") == null ? "" : body.group("scope");
    final String owner = scope.substring(scope.lastIndexOf('.') + 1);
    final LocalMethod method = new LocalMethod(body.group("name"), owner, 0, graph, false);
    final int arity =
        body.group("arity") == null
            ? method.parameters().size()
            : Integer.parseInt(body.group("arity"));
    return Optional.of(new LocalMethod(method.name, owner, arity, graph, false));
  }

  /** The key a call with this name and argument count looks up. */
  static String key(final String name, final int arity) {
    return name + "/" + arity;
  }

  String key() {
    return external ? owner + "#" + key(name, arity) : key(name, arity);
  }

  /**
   * The qualifiers under which a call names this method: in the same file, the owner or none; from
   * another file, the owner or a creation of it, written {@code new Owner()} in Java and {@code
   * Owner()} in Kotlin.
   */
  List<String> qualifiers() {
    if (external) {
      return List.of(owner, "(new" + owner + "())", owner + "()");
    }
    return owner.isEmpty() ? List.of("", THIS) : List.of("", THIS, owner);
  }

  /** The names of the parameters, in declaration order. */
  List<String> parameters() {
    final List<String> names = new ArrayList<>();
    for (final BasicBlock block : graph.blocks()) {
      for (final VariableAccess access : block.accesses()) {
        if (access.kind() == AccessKind.PARAMETER) {
          names.add(access.name());
        }
      }
    }
    return names;
  }

  /** Returns the graph with the named parameter marked as a source, so the analysis seeds it. */
  ControlFlowGraph withTaintedParameter(final String parameter) {
    final List<BasicBlock> blocks = new ArrayList<>();
    for (final BasicBlock block : graph.blocks()) {
      blocks.add(markedBlock(block, parameter));
    }
    return new ControlFlowGraph(graph.body(), graph.span(), blocks, graph.entry(), graph.exit());
  }

  private static BasicBlock markedBlock(final BasicBlock block, final String parameter) {
    final Optional<VariableAccess> access =
        block.accesses().stream()
            .filter(a -> a.kind() == AccessKind.PARAMETER && a.name().equals(parameter))
            .findFirst();
    if (access.isEmpty()) {
      return block;
    }
    final List<FlowSite> sites = new ArrayList<>(block.sites());
    sites.add(
        new FlowSite(
            FlowSiteKind.PARAMETER,
            parameter,
            "",
            Optional.empty(),
            List.of(MARK),
            List.of(),
            access.get().span()));
    return new BasicBlock(block.id(), block.nodes(), block.successors(), block.accesses(), sites);
  }
}
