package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The types that a Kotlin file declares for names its bodies share: its properties, which every
 * method of the class sees, and the return types of its functions. A name counts only when every
 * declaration of it in the file gives the same type, so a local or parameter that reuses the name of
 * a property cannot lend it the wrong type.
 *
 * <p>shortcut: names are matched across the whole file, not by class; upgrade with scope facts when
 * two classes of a file declare one name with one type each and the clash costs a finding.
 *
 * @param declared the type as written of each name with one declared or constructed type
 * @param constructed the type that each such name was created as, when it is a {@code val} that
 *     holds {@code Type()}
 * @param returns the declared return type of each function name that has one
 */
record KotlinFileTypes(
    Map<String,
    String> declared,
    Map<String,
    String> constructed,
    Map<String,
    String> returns
) {
  private static final String ASSIGN = "=";
  private static final String SIMPLE = "simpleIdentifier";
  private static final String TYPE = "type";
  private static final String VARIABLE = "variableDeclaration";

  KotlinFileTypes {
    declared = Map.copyOf(declared);
    constructed = Map.copyOf(constructed);
    returns = Map.copyOf(returns);
  }

  /** Reads the declarations of a file. */
  static KotlinFileTypes of(final SyntaxNode tree) {
    final Collector collector = new Collector();
    collector.visit(tree);
    return new KotlinFileTypes(
        collector.declared.resolved(),
        collector.constructed.resolved(),
        collector.returns.resolved()
    );
  }

  /** Names with the types seen for them; a name seen without a type or twice differently is lost. */
  private static final class NameTypes {
    private final Map<String, String> types = new HashMap<>();
    private final Set<String> lost = new HashSet<>();

    void note(final String name, final String type) {
      if (type == null || types.containsKey(name) && !types.get(name).equals(type)) {
        lost.add(name);
      } else {
        types.put(name, type);
      }
    }

    Map<String, String> resolved() {
      final Map<String, String> kept = new HashMap<>(types);
      kept.keySet().removeAll(lost);
      return kept;
    }
  }

  private static final class Collector {
    private final NameTypes declared = new NameTypes();
    private final NameTypes constructed = new NameTypes();
    private final NameTypes returns = new NameTypes();

    void visit(final SyntaxNode node) {
      switch (node.kind()) {
        case "propertyDeclaration" -> property(node);
        case VARIABLE -> variable(node, null);
        case "parameter" -> parameter(node);
        case "functionDeclaration" -> function(node);
        default -> node.children().forEach(this::visit);
      }
    }

    private void property(final SyntaxNode node) {
      final SyntaxNode variable = KotlinFlowSyntax.child(node, VARIABLE);
      final String created = variable == null ? "" : created(node, variable);
      for (final SyntaxNode child : node.children()) {
        if (child == variable) {
          variable(child, created.isEmpty() ? null : created);
        } else {
          visit(child);
        }
      }
    }

    /** The type that a {@code val} without a declared type constructs, or an empty string. */
    private static String created(final SyntaxNode property, final SyntaxNode variable) {
      if (KotlinFlowSyntax.child(variable, TYPE) != null
          || !KotlinFlowSyntax.hasToken(property, "val")) {
        return "";
      }
      final List<SyntaxNode> parts = KotlinFlowSyntax.parts(property);
      for (int index = 0; index < parts.size() - 1; index++) {
        if (ASSIGN.equals(KotlinFlowSyntax.tokenText(parts.get(index)))) {
          return KotlinCalls.createdType(parts.get(index + 1));
        }
      }
      return "";
    }

    private void variable(final SyntaxNode node, final String created) {
      final SyntaxNode name = KotlinFlowSyntax.child(node, SIMPLE);
      if (name == null) {
        return;
      }
      final String text = KotlinFlowSyntax.text(name);
      final SyntaxNode type = KotlinFlowSyntax.child(node, TYPE);
      if (type != null) {
        declared.note(text, KotlinCalls.erasedNullability(KotlinFlowSyntax.text(type)));
      } else {
        declared.note(text, created);
        if (created != null) {
          constructed.note(text, created);
        }
      }
    }

    private void parameter(final SyntaxNode node) {
      final SyntaxNode name = KotlinFlowSyntax.child(node, SIMPLE);
      final SyntaxNode type = KotlinFlowSyntax.child(node, TYPE);
      if (name != null) {
        declared.note(
            KotlinFlowSyntax.text(name),
            type == null ? null : KotlinCalls.erasedNullability(KotlinFlowSyntax.text(type))
        );
      }
      node.children().forEach(this::visit);
    }

    private void function(final SyntaxNode node) {
      final SyntaxNode name = KotlinFlowSyntax.child(node, "identifier");
      final SyntaxNode type = KotlinFlowSyntax.child(node, TYPE);
      if (name != null) {
        returns.note(
            KotlinFlowSyntax.text(name),
            type == null ? null : KotlinCalls.erasedNullability(KotlinFlowSyntax.text(type))
        );
      }
      node.children().forEach(this::visit);
    }
  }
}
