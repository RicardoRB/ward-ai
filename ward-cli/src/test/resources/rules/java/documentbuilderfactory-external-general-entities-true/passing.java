final class Check {
  Object use(javax.xml.parsers.DocumentBuilderFactory factory) throws Exception {
    return factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
  }
}
