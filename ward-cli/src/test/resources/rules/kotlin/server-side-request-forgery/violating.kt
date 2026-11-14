import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.RestTemplate

class Subject {
  fun direct(request: HttpServletRequest, rest: RestTemplate) {
    rest.getForObject(request.getParameter("x"), String::class.java)
  }

  fun propagated(request: HttpServletRequest, rest: RestTemplate) {
    val value = request?.getParameter("x") ?: ""
    val text = "<$value>"
    rest.getForObject(text, String::class.java)
  }

  fun annotated(@RequestParam value: String, rest: RestTemplate) {
    rest.getForObject("[${value.trim()}]", String::class.java)
  }

  fun crossMethod(request: HttpServletRequest, rest: RestTemplate) {
    val value = read(request)
    rest.getForObject(value, String::class.java)
  }

  fun read(request: HttpServletRequest): String? = request.getParameter("x")
}
