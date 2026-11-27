class Sample {
  int checked(@Nullable String text) {
    if (text == null) {
      return 0;
    }
    return text.length();
  }

  int plain(String text) {
    return text.length();
  }

  String passed(@Nullable String text) {
    return text;
  }
}


class Arguments {
  int count(final Map<String, @Nullable Object> properties) {
    return properties.size();
  }

  int list(List<@Nullable String> items) {
    return items.size();
  }

  int component(@Nullable String[] items) {
    return items.length;
  }

  int inner(Outer.@Nullable Inner value) {
    return value.size();
  }
}
