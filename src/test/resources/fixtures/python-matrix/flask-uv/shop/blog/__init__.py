from flask import Blueprint

bp = Blueprint("blog", __name__)


@bp.route("/posts")
def posts() -> str:
    return "posts"
