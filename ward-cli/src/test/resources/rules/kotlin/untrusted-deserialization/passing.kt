import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam
import org.yaml.snakeyaml.Yaml

class Subject {
  fun constant(request: HttpServletRequest, yaml: Yaml) {
    val unused = request.getParameter("x")
    yaml.load("fixed")
  }

  fun overwritten(request: HttpServletRequest, yaml: Yaml) {
    var value = request.getParameter("x")
    value = "fixed"
    yaml.load(value)
  }
}
