import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;

class Parsers {
  void sax(DocumentBuilderFactory factory) throws Exception {
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
  }

  void doctype(DocumentBuilderFactory factory) throws Exception {
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
  }

  void stax(XMLInputFactory factory) {
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
  }
}
