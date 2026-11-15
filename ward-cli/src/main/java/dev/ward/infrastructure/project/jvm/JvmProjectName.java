package dev.ward.infrastructure.project.jvm;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.ProjectNameReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Reads the root {@code artifactId} of a Maven project, or {@code rootProject.name} from the
 * settings file of a Gradle project.
 */
public final class JvmProjectName implements ProjectNameReader {
  private static final Pattern GRADLE_NAME =
      Pattern.compile("rootProject\\.name\\s*=\\s*[\"']([^\"']+)[\"']");

  /** Creates the reader. */
  @Inject
  public JvmProjectName() {
    // Stateless; the constructor exists for Dagger.
  }

  @Override
  public Optional<String> read(final Project project) {
    return project.buildTool() == BuildTool.MAVEN
        ? maven(project.root().resolve("pom.xml"))
        : gradle(project.root());
  }

  private static Optional<String> maven(final Path pom) {
    try (InputStream stream = Files.newInputStream(pom)) {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      final Element root = factory.newDocumentBuilder().parse(stream).getDocumentElement();
      for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child instanceof Element element && "artifactId".equals(element.getTagName())) {
          return Optional.of(element.getTextContent().strip()).filter(name -> !name.isEmpty());
        }
      }
      return Optional.empty();
    } catch (IOException | SAXException | ParserConfigurationException exception) {
      return Optional.empty();
    }
  }

  private static Optional<String> gradle(final Path root) {
    return Stream.of("settings.gradle.kts", "settings.gradle")
        .map(root::resolve)
        .filter(Files::isRegularFile)
        .map(JvmProjectName::first)
        .flatMap(Optional::stream)
        .findFirst();
  }

  private static Optional<String> first(final Path settings) {
    try {
      final Matcher matcher = GRADLE_NAME.matcher(Files.readString(settings));
      return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    } catch (IOException exception) {
      return Optional.empty();
    }
  }
}
