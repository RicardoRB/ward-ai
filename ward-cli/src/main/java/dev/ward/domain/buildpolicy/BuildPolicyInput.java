package dev.ward.domain.buildpolicy;

import dev.ward.domain.dependency.DependencyInventory;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What a rule reads.
 *
 * @param model the declarations of the build files
 * @param inventory the dependency inventory, resolved only when a rule asks for it
 * @param options the rule's options by name
 */
public record BuildPolicyInput(
    BuildModel model, Supplier<DependencyInventory> inventory, Map<String, String> options) {
  /** Copies the options. */
  public BuildPolicyInput {
    options = Map.copyOf(options);
  }
}
