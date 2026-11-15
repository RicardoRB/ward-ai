import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.RestTemplate

class Subject {
  fun constant(request: HttpServletRequest, rest: RestTemplate) {
    val unused = request.getParameter("x")
    rest.getForObject("fixed", String::class.java)
  }

  fun overwritten(request: HttpServletRequest, rest: RestTemplate) {
    var value = request.getParameter("x")
    value = "fixed"
    rest.getForObject(value, String::class.java)
  }
}
