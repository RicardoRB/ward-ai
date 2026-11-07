final class Check {
  Object use(javax.xml.parsers.DocumentBuilderFactory factory) throws Exception {
    return factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
  }
}
