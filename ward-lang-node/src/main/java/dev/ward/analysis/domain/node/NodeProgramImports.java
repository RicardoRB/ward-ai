package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ImportOrigin;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Follows a name that an import brings in to the constant another file exports, and knows the one
 * imported function that only rewrites its argument.
 *
 * <p>That function is the default export of {@code snarkdown}, whose documented behavior is to
 * turn a Markdown string into an HTML string with no other input, so its output is program data
 * when its argument is.
 */
final class NodeProgramImports {
  private static final Map<String, String> PURE_IMPORTS = Map.of("snarkdown", "default");

  private NodeProgramImports() {}

  /** Whether the callee is an imported function that only rewrites its argument. */
  static boolean pure(final NodeProgramFile file, final NodeProgramShapes.Call call) {
    final Optional<Binding> binding =
        NodeProgramShapes.name(call.callee()).flatMap(file::bindingOf);
    if (binding.isEmpty() || binding.get().kind() != BindingKind.IMPORT) {
      return false;
    }
    final Optional<ImportOrigin> origin = binding.get().origin();
    return origin.isPresent()
        && origin.get().member().equals(PURE_IMPORTS.get(origin.get().specifier()));
  }

  /** The values of the constant that another file exports under the imported name. */
  static Optional<List<NodeProgramValues.Term>> imported(
      final NodeProgramValues values,
      final NodeProgramFile file,
      final Binding binding,
      final int depth
  ) {
    final Optional<ImportOrigin> origin = binding.origin();
    if (origin.isEmpty() || origin.get().member().isEmpty()) {
      return Optional.empty();
    }
    final Optional<NodeProgramFile> other = values.files().imported(file, origin.get().specifier());
    final Optional<Binding> exported =
        other.flatMap(target -> exportedConstant(target, origin.get().member()));
    if (exported.isEmpty() || !values.seal().sealed(other.get(), exported.get())) {
      return Optional.empty();
    }
    return NodeProgramBindings.variable(values, other.get(), exported.get(), depth);
  }

  private static Optional<Binding> exportedConstant(final NodeProgramFile file, final String name) {
    return file.scopes().module().bindings().stream()
        .filter(binding -> binding.name().equals(name))
        .filter(
            binding -> binding.kind() == BindingKind.CONSTANT || binding.kind() == BindingKind.VARIABLE
        )
        .filter(binding -> binding.has(BindingFlag.CONST))
        .filter(
            binding -> NodeProgramBindings.declarationOf(file, binding).filter(
                file::exported
            ).isPresent()
        )
        .findFirst();
  }
}
