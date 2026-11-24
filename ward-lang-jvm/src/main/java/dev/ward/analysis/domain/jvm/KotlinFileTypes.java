package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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
 * @param extensions the extension functions, each written {@code name/arity} with the receiver
 *     counted as a parameter
 * @param allowLists the {@code val} properties that hold {@code setOf(...)} or {@code listOf(...)}
 */
record KotlinFileTypes(
    Map<String,
    String> declared,
    Map<String,
    String> constructed,
    Map<String,
    String> returns,
    Set<String> extensions,
    Set<String> allowLists
) {
  private static final String ASSIGN = "=";
  private static final String SIMPLE = "simpleIdentifier";
  private static final String TYPE = "type";
  private static final String VARIABLE = "variableDeclaration";
  private static final Pattern ALLOW_LIST =
      Pattern.compile("^(?:setOf|listOf|persistentSetOf|persistentListOf)(?:<[^()]*>)?\\(");

  KotlinFileTypes {
    declared = Map.copyOf(declared);
    constructed = Map.copyOf(constructed);
    returns = Map.copyOf(returns);
    extensions = Set.copyOf(extensions);
    allowLists = Set.copyOf(allowLists);
  }

  /** Reads the declarations of a file. */
  static KotlinFileTypes of(final SyntaxNode tree, final JavaTypeNames types) {
    final Collector collector = new Collector(types);
    collector.visit(tree);
    return new KotlinFileTypes(
        collector.declared.resolved(),
        collector.constructed.resolved(),
        collector.returns.resolved(),
        collector.extensions,
        collector.allowLists
    );
  }

  /** Names with the types seen for them; a name seen without a type or twice differently is lost. */
  private static final class NameTypes {
    private final Map<String, String> types = new HashMap<>();
    private final Set<String> lost = new HashSet<>();
    private final JavaTypeNames names;

    NameTypes(final JavaTypeNames names) {
      this.names = names;
    }

    /** Two spellings of one type, {@code Logger} and its qualified name, are one type. */
    private boolean same(final String first, final String second) {
      return first.equals(second)
          || names.resolve(first).equals(names.resolve(second));
    }

    void note(final String name, final String type) {
      if (type == null || types.containsKey(name) && !same(types.get(name), type)) {
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

  private static int parameterCount(final SyntaxNode function) {
    final SyntaxNode list = KotlinFlowSyntax.child(function, "functionValueParameters");
    return list == null ? 0 : KotlinFlowSyntax.children(list, "functionValueParameter").size();
  }

  /** The type after the colon of a function's signature; a receiver type is not a return type. */
  private static SyntaxNode returnType(final SyntaxNode function) {
    boolean afterColon = false;
    for (final SyntaxNode part : KotlinFlowSyntax.parts(function)) {
      if (part.children().isEmpty() && ":".equals(KotlinFlowSyntax.tokenText(part))) {
        afterColon = true;
      } else if (afterColon && TYPE.equals(part.kind())) {
        return part;
      }
    }
    return null;
  }

  private static final class Collector {
    private final NameTypes declared;
    private final NameTypes constructed;
    private final NameTypes returns;
    private final Set<String> extensions = new HashSet<>();
    private final Set<String> allowLists = new HashSet<>();
    private final JavaTypeNames types;

    Collector(final JavaTypeNames types) {
      this.types = types;
      this.declared = new NameTypes(types);
      this.constructed = new NameTypes(types);
      this.returns = new NameTypes(types);
    }

    void visit(final SyntaxNode node) {
      switch (node.kind()) {
        case "propertyDeclaration" -> property(node);
        case VARIABLE -> variable(node, null, "");
        case "parameter", "classParameter" -> parameter(node);
        case "functionDeclaration" -> function(node);
        default -> node.children().forEach(this::visit);
      }
    }

    private void property(final SyntaxNode node) {
      final SyntaxNode variable = KotlinFlowSyntax.child(node, VARIABLE);
      final String created = variable == null ? "" : created(node, variable);
      final String factory = variable == null || !created.isEmpty() ? "" : factory(node, variable);
      if (variable != null) {
        allowList(node, variable);
      }
      for (final SyntaxNode child : node.children()) {
        if (child == variable) {
          variable(child, created.isEmpty() ? null : created, factory);
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

    /** Notes a {@code val} that holds the constants of {@code setOf} or {@code listOf}. */
    private void allowList(final SyntaxNode property, final SyntaxNode variable) {
      final SyntaxNode name = KotlinFlowSyntax.child(variable, SIMPLE);
      if (name == null || !KotlinFlowSyntax.hasToken(property, "val")) {
        return;
      }
      final List<SyntaxNode> parts = KotlinFlowSyntax.parts(property);
      for (int index = 0; index < parts.size() - 1; index++) {
        if (ASSIGN.equals(KotlinFlowSyntax.tokenText(parts.get(index)))
            && ALLOW_LIST
                .matcher(KotlinFlowSyntax.text(parts.get(index + 1)).replaceAll("\\s", ""))
                .find()) {
          allowLists.add(KotlinFlowSyntax.text(name));
        }
      }
    }

    /** The type of a well-known factory call that initializes a {@code val}, or an empty string. */
    private String factory(final SyntaxNode property, final SyntaxNode variable) {
      if (KotlinFlowSyntax.child(variable, TYPE) != null
          || !KotlinFlowSyntax.hasToken(property, "val")) {
        return "";
      }
      final List<SyntaxNode> parts = KotlinFlowSyntax.parts(property);
      for (int index = 0; index < parts.size() - 1; index++) {
        if (ASSIGN.equals(KotlinFlowSyntax.tokenText(parts.get(index)))) {
          return KotlinFactoryTypes.returnType(KotlinFlowSyntax.text(parts.get(index + 1)), types)
              .orElse("");
        }
      }
      return "";
    }

    private void variable(final SyntaxNode node, final String created, final String factory) {
      final SyntaxNode name = KotlinFlowSyntax.child(node, SIMPLE);
      if (name == null) {
        return;
      }
      final String text = KotlinFlowSyntax.text(name);
      final SyntaxNode type = KotlinFlowSyntax.child(node, TYPE);
      if (type != null) {
        declared.note(text, KotlinCalls.erasedNullability(KotlinFlowSyntax.text(type)));
      } else {
        declared.note(text, created == null && !factory.isEmpty() ? factory : created);
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
      final SyntaxNode type = returnType(node);
      if (name != null && KotlinExtensions.receiverOf(node).isPresent()) {
        extensions.add(KotlinFlowSyntax.text(name) + "/" + (parameterCount(node) + 1));
      }
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
