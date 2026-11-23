package dev.ward.analysis.application.rust;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckProvider;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rust.RustGetFirstCheck;
import java.util.Map;

/** The declarative checks that only Rust rules use. */
public final class RustCheckProvider implements CheckProvider {
  @Override
  public Map<String, CheckFactory> checks() {
    return Map.of(
        "rust-get-first",
        toml -> new RustGetFirstCheck(TomlValues.required(toml, "message"))
    );
  }
}
