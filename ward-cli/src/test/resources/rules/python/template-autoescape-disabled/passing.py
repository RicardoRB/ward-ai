import jinja2


def environment():
    return jinja2.Environment(loader=jinja2.FileSystemLoader("."), autoescape=jinja2.select_autoescape())


def template(source):
    return jinja2.Template(source, autoescape=True)
