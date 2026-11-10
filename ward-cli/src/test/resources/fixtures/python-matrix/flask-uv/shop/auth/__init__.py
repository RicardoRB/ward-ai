"""Fixture code for the Python matrix."""

from flask import Blueprint

from shop.pricing import total

bp = Blueprint("auth", __name__)


@bp.route("/login")
def login() -> str:
    """Answers the login route."""
    return "login"


@bp.route("/price")
def price() -> str:
    """Answers the price route."""
    return str(total(1, 2))
