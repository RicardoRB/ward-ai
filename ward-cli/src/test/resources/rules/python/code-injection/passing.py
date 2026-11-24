import ast
from flask import request


def calculate():
    expression = request.args.get("expr")
    return ast.literal_eval(expression)


def constant():
    return eval("1 + 1")


def number():
    value = float(request.args.get("x"))
    return eval(str(value))


def database(session, user_id):
    statement = select(Item).where(Item.owner_id == user_id)
    session.exec(statement)
    session.exec(request.args.get("q"))
    self.session.exec(request.args["q"])
