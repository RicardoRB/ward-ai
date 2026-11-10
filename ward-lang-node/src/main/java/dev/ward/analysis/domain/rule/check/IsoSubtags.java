package dev.ward.analysis.domain.rule.check;

import java.util.Set;

/**
 * The ISO 639 language, ISO 15924 script and ISO 3166 country codes that Biome 2.5.15 accepts in a
 * language tag, as its {@code biome_aria_metadata} crate lists them. The codes are matched exactly,
 * as Biome matches them.
 */
final class IsoSubtags {

  private static final Set<String> LANGUAGES =
      Set.of(
          "ab", "aa", "af", "sq", "am", "ar", "an", "hy", "as", "ay", "az", "ba", "eu", "bn", "dz",
          "bh", "bi", "br", "bg", "my", "be", "km", "ca", "zh", "co", "hr", "cs", "da", "nl", "en",
          "eo", "et", "fo", "fa", "fj", "fi", "fr", "fy", "gl", "gd", "gv", "ka", "de", "el", "kl",
          "gn", "gu", "ht", "ha", "he", "iw", "hi", "hu", "is", "io", "id", "in", "ia", "ie", "iu",
          "ik", "ga", "it", "ja", "jv", "kn", "ks", "kk", "rw", "ky", "rn", "ko", "ku", "lo", "la",
          "lv", "li", "ln", "lt", "mk", "mg", "ms", "ml", "mt", "mi", "mr", "mo", "mn", "na", "ne",
          "no", "nb", "nn", "oc", "or", "om", "ps", "pl", "pt", "pa", "qu", "rm", "ro", "ru", "sm",
          "sg", "sa", "sr", "sh", "st", "tn", "sn", "ii", "sd", "si", "ss", "sk", "sl", "so", "es",
          "su", "sw", "sv", "tl", "tg", "ta", "tt", "te", "th", "bo", "ti", "to", "ts", "tr", "tk",
          "tw", "ug", "uk", "ur", "uz", "vi", "vo", "wa", "cy", "wo", "xh", "yi", "ji", "yo", "zu");

  private static final Set<String> SCRIPTS =
      Set.of(
          "Arab", "Armn", "Beng", "Cyrl", "Deva", "Ethi", "Grek", "Gujr", "Guru", "Hang", "Hani",
          "Hans", "Hant", "Hebr", "Hira", "Kana", "Khmr", "Laoo", "Latn", "Mlym", "Mymr", "Orya",
          "Sinh", "Taml", "Telu", "Thai", "Tibt", "Zyyy");

  private static final Set<String> COUNTRIES =
      Set.of(
          "AF", "AL", "DZ", "AS", "AD", "AO", "AI", "AQ", "AG", "AR", "AM", "AW", "AU", "AT", "AZ",
          "BS", "BH", "BD", "BB", "BY", "BE", "BZ", "BJ", "BM", "BT", "BO", "BA", "BW", "BR", "IO",
          "VG", "BN", "BG", "BF", "MM", "BI", "KH", "CM", "CA", "CV", "KY", "CF", "TD", "CL", "CN",
          "CX", "CC", "CO", "KM", "CK", "CR", "HR", "CU", "CY", "CZ", "CD", "DK", "DJ", "DM", "DO",
          "EC", "EG", "SV", "GQ", "ER", "EE", "ET", "FK", "FO", "FJ", "FI", "FR", "PF", "GA", "GM",
          "GE", "DE", "GH", "GI", "GR", "GL", "GD", "GU", "GT", "GN", "GW", "GY", "HT", "VA", "HN",
          "HK", "HU", "IS", "IN", "ID", "IR", "IQ", "IE", "IM", "IL", "IT", "CI", "JM", "JP", "JE",
          "JO", "KZ", "KE", "KI", "KW", "KG", "LA", "LV", "LB", "LS", "LR", "LY", "LI", "LT", "LU",
          "MO", "MK", "MG", "MW", "MY", "MV", "ML", "MT", "MH", "MR", "MU", "YT", "MX", "FM", "MD",
          "MC", "MN", "ME", "MS", "MA", "MZ", "NA", "NR", "NP", "NL", "AN", "NC", "NZ", "NI", "NE",
          "NG", "NU", "KP", "MP", "NO", "OM", "PK", "PW", "PA", "PG", "PY", "PE", "PH", "PN", "PL",
          "PT", "PR", "QA", "CG", "RO", "RU", "RW", "BL", "SH", "KN", "LC", "MF", "PM", "VC", "WS",
          "SM", "ST", "SA", "SN", "RS", "SC", "SL", "SG", "SK", "SI", "SB", "SO", "ZA", "KR", "ES",
          "LK", "SD", "SR", "SJ", "SZ", "SE", "CH", "SY", "TW", "TJ", "TZ", "TH", "TL", "TG", "TK",
          "TO", "TT", "TN", "TR", "TM", "TC", "TV", "UG", "UA", "AE", "GB", "US", "UY", "VI", "UZ",
          "VU", "VE", "VN", "WF", "EH", "YE", "ZM", "ZW");

  private IsoSubtags() {}

  /** Whether the code is a language code. */
  static boolean isLanguage(final String code) {
    return LANGUAGES.contains(code);
  }

  /** Whether the code is a script code. */
  static boolean isScript(final String code) {
    return SCRIPTS.contains(code);
  }

  /** Whether the code is a country code. */
  static boolean isCountry(final String code) {
    return COUNTRIES.contains(code);
  }
}
