from flask import Blueprint

from shop.pricing import total

bp = Blueprint("auth", __name__)


@bp.route("/login")
def login() -> str:
    return "login"


@bp.route("/price")
def price() -> str:
    return str(total(1, 2))
