from flask import request


def calculate():
    expression = request.args.get("expr")
    return eval(expression)


def run_snippet():
    code = request.form["code"]
    exec(code)


def direct():
    return eval(request.args["x"])


def builtin_module():
    import builtins
    builtins.exec(request.args["c"])
    builtins.eval(request.args["e"])
