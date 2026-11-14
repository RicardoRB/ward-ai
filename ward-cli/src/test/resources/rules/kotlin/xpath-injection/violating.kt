import javax.servlet.http.HttpServletRequest
import javax.xml.xpath.XPath
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun direct(request: HttpServletRequest, xpath: XPath, document: Any) {
    xpath.evaluate(request.getParameter("x"), document)
  }

  fun propagated(request: HttpServletRequest, xpath: XPath, document: Any) {
    val value = request?.getParameter("x") ?: ""
    val text = "<$value>"
    xpath.evaluate(text, document)
  }

  fun annotated(@RequestParam value: String, xpath: XPath, document: Any) {
    xpath.evaluate("[${value.trim()}]", document)
  }

  fun crossMethod(request: HttpServletRequest, xpath: XPath, document: Any) {
    val value = read(request)
    xpath.evaluate(value, document)
  }

  fun read(request: HttpServletRequest): String? = request.getParameter("x")
}
