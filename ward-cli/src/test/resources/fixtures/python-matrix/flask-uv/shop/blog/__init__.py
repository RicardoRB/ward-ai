from flask import Blueprint

bp = Blueprint("blog", __name__)


@bp.route("/posts")
def posts() -> str:
    """Answers the posts route."""
    return "posts"
