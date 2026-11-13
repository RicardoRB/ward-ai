import javax.servlet.http.HttpServletRequest;
import javax.xml.xpath.XPath;

class Lookup {
  Object constant(XPath xpath, Object document) throws Exception {
    return xpath.evaluate("//user[@name='admin']", document);
  }

  Object numeric(HttpServletRequest request, XPath xpath, Object document) throws Exception {
    int index = Integer.parseInt(request.getParameter("index"));
    return xpath.evaluate("//user[" + index + "]", document);
  }
}
