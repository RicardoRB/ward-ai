package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The declared types of one body, from type annotations and {@code new} initializers. A name
 * resolves to a receiver type only through the file's named imports.
 */
final class NodeTypes {

  private static final String SINGLE = "singleExpression";
  private static final String NEW = "new";
  private static final String TYPE_ANNOTATION = "typeAnnotation";

  private final NodeImports imports;
  private final Map<String, String> declared = new HashMap<>();

  /**
   * Creates the types of one body.
   *
   * @param imports the named imports of the file
   */
  NodeTypes(final NodeImports imports) {
    this.imports = imports;
  }

  /**
   * Records the type of a parameter from its annotation, when it has one.
   *
   * @param name the parameter name
   * @param formal the parameter node
   */
  void declareParameter(final String name, final SyntaxNode formal) {
    final SyntaxNode type = NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(formal), TYPE_ANNOTATION);
    if (type != null) {
      declared.put(name, typeText(type));
    }
  }

  /**
   * Records the type of a variable from its type annotation, or else from its {@code new} class.
   *
   * @param declaration the declaration node
   */
  void declareVariable(final SyntaxNode declaration) {
    final SyntaxNode name = NodeSyntax.leafOf(declaration);
    if (name == null) {
      return;
    }
    final SyntaxNode type =
        NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(declaration), TYPE_ANNOTATION);
    if (type != null) {
      declared.put(NodeFlowSyntax.text(name), typeText(type));
    } else {
      newType(declaration).ifPresent(written -> declared.put(NodeFlowSyntax.text(name), written));
    }
  }

  /**
   * Returns the receiver type of a qualifier: the import its declared type names, or the module the
   * name, or the first segment of a dotted name, is bound to.
   *
   * @param qualifier the qualifier text as written
   * @return the module type, or empty when the qualifier is unknown
   */
  Optional<String> receiverType(final String qualifier) {
    if (qualifier.isEmpty()) {
      return Optional.empty();
    }
    final String written = declared.getOrDefault(qualifier, qualifier);
    return Optional.ofNullable(imports.typeOf(written)).or(() -> imports.moduleOf(root(written)));
  }

  /** The module of an unqualified callee that an import or {@code require()} binds, if any. */
  Optional<String> calleeReceiver(final String name) {
    return imports.moduleOf(name);
  }

  /** The name that a call through a local name calls: the imported name, or the name itself. */
  String calleeName(final String name) {
    return imports.calledName(name);
  }

  /** The first segment of a dotted qualifier, so {@code fs.promises} is the module {@code fs}. */
  private static String root(final String dotted) {
    final int dot = dotted.indexOf('.');
    return dot < 0 ? dotted : dotted.substring(0, dot);
  }

  /** The class of a {@code new X(...)} initializer, when the declaration has one. */
  private static Optional<String> newType(final SyntaxNode node) {
    for (final SyntaxNode child : node.children()) {
      if (SINGLE.equals(child.kind())) {
        final List<SyntaxNode> parts = NodeFlowSyntax.parts(child);
        if (parts.size() >= 2 && NEW.equals(NodeFlowSyntax.text(parts.get(0)))) {
          return Optional.of(NodeFlowSyntax.text(parts.get(1)));
        }
      }
    }
    return Optional.empty();
  }

  /** The written type of an annotation, without its colon, spaces and type arguments. */
  private static String typeText(final SyntaxNode annotation) {
    final String compact = NodeFlowSyntax.text(annotation).replace(":", "").replaceAll("\\s", "");
    final int generics = compact.indexOf('<');
    return generics < 0 ? compact : compact.substring(0, generics);
  }
}
