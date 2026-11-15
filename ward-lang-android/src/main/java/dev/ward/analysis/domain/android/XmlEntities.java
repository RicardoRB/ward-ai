package dev.ward.analysis.domain.android;

import java.util.Map;

/** Resolves the five predefined entities and the numeric character references of XML text. */
final class XmlEntities {

  private static final int HEX_RADIX = 16;
  private static final int DECIMAL_RADIX = 10;
  private static final Map<String, String> NAMED =
      Map.of("lt", "<", "gt", ">", "amp", "&", "quot", "\"", "apos", "'");

  private XmlEntities() {}

  /**
   * Returns {@code raw} with its references resolved.
   *
   * @param raw the text between two delimiters
   * @param offset the offset of {@code raw} in the file, for the position of a problem
   * @throws XmlSyntaxException for a reference that is unterminated, unknown or invalid
   */
  static String decode(final String raw, final int offset) {
    if (raw.indexOf('&') < 0) {
      return raw;
    }
    final StringBuilder out = new StringBuilder(raw.length());
    int index = 0;
    while (index < raw.length()) {
      final int amp = raw.indexOf('&', index);
      if (amp < 0) {
        out.append(raw, index, raw.length());
        break;
      }
      out.append(raw, index, amp);
      final int semicolon = raw.indexOf(';', amp);
      if (semicolon < 0) {
        throw new XmlSyntaxException("unterminated reference", offset + amp);
      }
      out.append(reference(raw.substring(amp + 1, semicolon), offset + amp));
      index = semicolon + 1;
    }
    return out.toString();
  }

  private static String reference(final String name, final int offset) {
    final String named = NAMED.get(name);
    if (named != null) {
      return named;
    }
    if (name.startsWith("#")) {
      return numeric(name, offset);
    }
    throw new XmlSyntaxException("unknown entity &" + name + ";", offset);
  }

  private static String numeric(final String name, final int offset) {
    final boolean hex = name.startsWith("#x");
    final String digits = name.substring(hex ? 2 : 1);
    try {
      return new String(
          Character.toChars(Integer.parseInt(digits, hex ? HEX_RADIX : DECIMAL_RADIX)));
    } catch (final IllegalArgumentException invalid) {
      throw new XmlSyntaxException("invalid character reference &" + name + ";", offset, invalid);
    }
  }
}
