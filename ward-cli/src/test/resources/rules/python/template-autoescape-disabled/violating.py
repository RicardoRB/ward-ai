import jinja2


def environment():
    return jinja2.Environment(loader=jinja2.FileSystemLoader("."), autoescape=False)


def template(source):
    return jinja2.Template(source, autoescape=False)
