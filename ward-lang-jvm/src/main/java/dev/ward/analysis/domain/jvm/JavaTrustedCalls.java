package dev.ward.analysis.domain.jvm;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds calls whose value does not come from the remote user: {@code System.getProperty} of a
 * standard key that the JVM sets from the operating system, and {@code getRequestURI}, which is
 * the path as sent, undecoded, and cannot hold a line break. Such a call gives a site that covers
 * the call itself, so a taint specification that lists the site as a sanitizer does not take the
 * call as a source.
 */
final class JavaTrustedCalls {
  /** The name of the site of a {@code System.getProperty} call with a standard constant key. */
  static final String PROPERTY = "<jvm-property>";

  /** The name of the site of a {@code getRequestURI} call. */
  static final String REQUEST_URI = "<undecoded-uri>";

  /** The name of the site of a percent-decode of a {@code getRequestURI} call. */
  static final String DECODED_URI = "<decoded-uri>";

  private static final Set<String> DECODER_TYPES =
      Set.of("java.net.URLDecoder", "org.springframework.web.util.UriUtils");
  private static final Pattern REQUEST_URI_CALL = Pattern.compile("[\\w.]+\\.getRequestURI\\(\\)");
  private static final String SYSTEM_TYPE = "java.lang.System";
  private static final int WITH_DEFAULT = 2;
  private static final Set<String> STANDARD_KEYS =
      Set.of(
          "\"user.dir\"",
          "\"user.home\"",
          "\"java.io.tmpdir\"",
          "\"line.separator\"",
          "\"file.separator\"",
          "\"path.separator\"",
          "\"os.name\""
      );

  private JavaTrustedCalls() {}

  /**
   * Returns whether a call is {@code System.getProperty} of a standard constant key.
   *
   * @param name the called name
   * @param receiverType the resolved type of the receiver
   * @param arguments the text of the arguments
   */
  static boolean isStandardProperty(
      final String name,
      final Optional<String> receiverType,
      final List<String> arguments
  ) {
    return "getProperty".equals(name)
        && receiverType.filter(SYSTEM_TYPE::equals).isPresent()
        && !arguments.isEmpty()
        && arguments.size() <= WITH_DEFAULT
        && STANDARD_KEYS.contains(arguments.get(0));
  }

  /**
   * Returns whether a call is {@code getRequestURI()} on some receiver.
   *
   * @param name the called name
   * @param hasReceiver whether the call has a receiver
   * @param arguments the text of the arguments
   */
  static boolean isRequestUri(
      final String name,
      final boolean hasReceiver,
      final List<String> arguments
  ) {
    return hasReceiver && "getRequestURI".equals(name) && arguments.isEmpty();
  }

  /**
   * Returns whether a call is {@code URLDecoder.decode} or {@code UriUtils.decode} of a direct
   * {@code getRequestURI()} call: decoding turns {@code %0A} into a line break.
   *
   * @param name the called name
   * @param receiverType the resolved type of the receiver
   * @param arguments the text of the arguments
   */
  static boolean isDecodedRequestUri(
      final String name,
      final Optional<String> receiverType,
      final List<String> arguments
  ) {
    return "decode".equals(name)
        && receiverType.filter(DECODER_TYPES::contains).isPresent()
        && !arguments.isEmpty()
        && REQUEST_URI_CALL.matcher(arguments.get(0)).matches();
  }
}
