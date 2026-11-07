package dev.ward.analysis.domain.node.types;

/** A scope in which names resolve to declarations: a module, a namespace or the global scope. */
interface Lookup {

  /** Returns the declarations of {@code name} visible here, or null when there are none. */
  Sym find(String name);

  /** Returns the declarations of {@code name} that this scope exports, or null. */
  Sym exported(String name);
}
