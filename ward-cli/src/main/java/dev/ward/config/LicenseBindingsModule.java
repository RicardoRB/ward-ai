package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.license.LicenseCatalog;
import dev.ward.domain.license.LicenseMetadata;
import dev.ward.domain.license.LicenseTexts;
import dev.ward.infrastructure.cli.licenses.LicensesCommand;
import dev.ward.infrastructure.cli.sbom.SbomCommand;
import dev.ward.infrastructure.license.BundledLicenseCatalog;
import dev.ward.infrastructure.license.BundledLicenseTexts;
import dev.ward.infrastructure.license.jvm.PomLicenseMetadata;
import dev.ward.infrastructure.license.node.NodeLicenseMetadata;
import dev.ward.infrastructure.license.python.PythonLicenseMetadata;
import dev.ward.infrastructure.license.swift.SwiftLicenseMetadata;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * Binds the license lookup: the bundled SPDX data, one metadata reader per ecosystem with an
 * explicit constructor graph, and the {@code sbom} and {@code licenses} commands.
 */
@Module
abstract class LicenseBindingsModule {
  private static final String GRADLE_CACHE = ".gradle/caches/modules-2/files-2.1";
  private static final String MAVEN_REPOSITORY = ".m2/repository";

  @Binds
  abstract LicenseCatalog licenseCatalog(BundledLicenseCatalog implementation);

  @Binds
  abstract LicenseTexts licenseTexts(BundledLicenseTexts implementation);

  /** Registers the {@code ward sbom} command with the command factory. */
  @Binds
  @IntoMap
  @ClassKey(SbomCommand.class)
  abstract Object sbomCommand(SbomCommand command);

  /** Registers the {@code ward licenses} command with the command factory. */
  @Binds
  @IntoMap
  @ClassKey(LicensesCommand.class)
  abstract Object licensesCommand(LicensesCommand command);

  @Provides
  static Map<Ecosystem, LicenseMetadata> licenseReaders(
      final JsonCodec codec, final LicenseCatalog catalog, final LicenseTexts texts) {
    final Path home = Path.of(System.getProperty("user.home", ""));
    final String gradleHome = System.getenv("GRADLE_USER_HOME");
    final Path gradleCache =
        gradleHome == null
            ? home.resolve(GRADLE_CACHE)
            : Path.of(gradleHome).resolve("caches/modules-2/files-2.1");
    final Map<Ecosystem, LicenseMetadata> readers = new EnumMap<>(Ecosystem.class);
    readers.put(
        Ecosystem.MAVEN, new PomLicenseMetadata(home.resolve(MAVEN_REPOSITORY), gradleCache));
    readers.put(Ecosystem.NPM, new NodeLicenseMetadata(codec));
    readers.put(Ecosystem.PYPI, new PythonLicenseMetadata(catalog, System.getenv("VIRTUAL_ENV")));
    readers.put(Ecosystem.SWIFT_URL, new SwiftLicenseMetadata(texts));
    return readers;
  }
}
