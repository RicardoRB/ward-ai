import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam
import org.yaml.snakeyaml.Yaml

class Subject {
  fun direct(request: HttpServletRequest, yaml: Yaml) {
    yaml.load(request.getParameter("x"))
  }

  fun propagated(request: HttpServletRequest, yaml: Yaml) {
    val value = request?.getParameter("x") ?: ""
    val text = "<$value>"
    yaml.load(text)
  }

  fun annotated(@RequestParam value: String, yaml: Yaml) {
    yaml.load("[${value.trim()}]")
  }

  fun crossMethod(request: HttpServletRequest, yaml: Yaml) {
    val value = read(request)
    yaml.load(value)
  }

  fun read(request: HttpServletRequest): String? = request.getParameter("x")
}
