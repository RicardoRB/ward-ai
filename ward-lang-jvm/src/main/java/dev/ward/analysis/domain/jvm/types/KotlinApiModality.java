package dev.ward.analysis.domain.jvm.types;

/**
 * What the modifiers of a Kotlin member say about overriding: members of a class are closed unless
 * {@code open}, {@code abstract} or {@code override}, and members of an interface are open.
 */
record KotlinApiModality(boolean inInterface) {

  boolean isFinal(final KotlinApiModifiers modifiers) {
    if (inInterface) {
      return false;
    }
    final boolean open =
        modifiers.has("open") || modifiers.has("abstract") || modifiers.has("override");
    return modifiers.has("final") || !open;
  }

  boolean isAbstract(final KotlinApiModifiers modifiers, final boolean body) {
    return modifiers.has("abstract") || inInterface && !body;
  }

  boolean isDefault(final KotlinApiModifiers modifiers, final boolean body) {
    return inInterface && body && !modifiers.has("private");
  }
}
