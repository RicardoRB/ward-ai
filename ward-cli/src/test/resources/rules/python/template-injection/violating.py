from flask import render_template_string, request


def hello():
    name = request.args.get("name")
    return render_template_string("<h1>Hello " + name + "</h1>")


def page():
    return render_template_string(request.form["template"])
