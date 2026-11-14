package dev.ward.analysis.application.android;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckProvider;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.android.rule.AllowBackupCheck;
import dev.ward.analysis.domain.android.rule.ApplicationFlagCheck;
import dev.ward.analysis.domain.android.rule.ContentDescriptionCheck;
import dev.ward.analysis.domain.android.rule.ExportedComponentCheck;
import dev.ward.analysis.domain.android.rule.HardcodedTextCheck;
import dev.ward.analysis.domain.android.rule.NetworkConfigCleartextCheck;
import dev.ward.analysis.domain.android.rule.UnusedResourcesCheck;
import java.util.Map;

/** The checks of the Android rules, named {@code android-} followed by what they look for. */
public final class AndroidCheckProvider implements CheckProvider {

  private static final String MESSAGE = "message";

  @Override
  public Map<String, CheckFactory> checks() {
    return Map.of(
        "android-exported-component",
        toml -> new ExportedComponentCheck(TomlValues.required(toml, "component")),
        "android-application-flag",
        toml ->
            new ApplicationFlagCheck(
                TomlValues.required(toml, "attribute"), TomlValues.required(toml, MESSAGE)),
        "android-cleartext-traffic",
        toml -> new NetworkConfigCleartextCheck(),
        "android-allow-backup",
        toml -> new AllowBackupCheck(),
        "android-hardcoded-text",
        toml -> new HardcodedTextCheck(),
        "android-content-description",
        toml -> new ContentDescriptionCheck(),
        "android-unused-resources",
        toml -> new UnusedResourcesCheck());
  }
}
