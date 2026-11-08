package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The members that expressions reach through a module, a class or a value, and calls of stubs. */
final class PythonMembers {

  private final PythonTypeIndex index;
  private final PythonStubs stubs;
  private final PythonSignatures signatures;

  /** Creates the members over the indexed file, its stubs and their signatures. */
  PythonMembers(
      final PythonTypeIndex index, final PythonStubs stubs, final PythonSignatures signatures) {
    this.index = index;
    this.stubs = stubs;
    this.signatures = signatures;
  }

  /** The member {@code member} of {@code current}, when a stub declares it. */
  PythonSubject attribute(final PythonSubject current, final String member) {
    if (current.kind() == PythonSubject.Kind.MODULE) {
      return moduleMember(current.name(), member);
    }
    final TypeRef owner = current.kind() == PythonSubject.Kind.VALUE ? current.type() : null;
    if (owner == null || owner.arrayDepth() != 0) {
      return PythonSubject.UNKNOWN;
    }
    return stubs
        .method(owner.name(), member)
        .map(function -> PythonSubject.stubFunction(function, owner.name()))
        .orElse(PythonSubject.UNKNOWN);
  }

  /** The member {@code member} of {@code module}: a function, a class, or a submodule. */
  PythonSubject moduleMember(final String module, final String member) {
    final String qualified = module + "." + member;
    final Optional<PythonStubs.StubFunction> function = stubs.function(module, member);
    if (function.isPresent()) {
      return PythonSubject.stubFunction(function.get(), null);
    }
    return stubs.isClass(qualified)
        ? PythonSubject.klass(qualified)
        : PythonSubject.module(qualified);
  }

  /** The result of calling a stub function: its return type and the call target. */
  PythonSubject invokeStub(final PythonSubject current) {
    final SyntaxNode definition = current.definition();
    final boolean method = current.name() != null;
    final TypeRef returns = signatures.returns(definition, current.module()).orElse(null);
    final String owner = method ? current.name() : current.module();
    final CallTarget call =
        new CallTarget(
            TypeRef.of(owner),
            PythonNames.identifier(definition.children().get(1)),
            signatures.parameters(definition, current.module(), method),
            returns,
            false,
            false,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            Map.of(),
            Map.of(),
            null);
    return PythonSubject.called(returns, call);
  }

  /** The result of calling the class {@code name}: an instance of it, unless it is shadowed. */
  PythonSubject instance(final String name) {
    if (index.declaresClass(name) && stubs.isClass(name)) {
      return PythonSubject.UNKNOWN;
    }
    return PythonSubject.value(TypeRef.of(name));
  }
}
