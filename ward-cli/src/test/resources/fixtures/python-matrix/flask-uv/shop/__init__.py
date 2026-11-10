"""Fixture code for the Python matrix."""

from flask import Flask

from shop.auth import bp as auth_bp
from shop.blog import bp as blog_bp


def create_app() -> Flask:
    """Builds the application with its blueprints."""
    app = Flask(__name__)
    app.register_blueprint(auth_bp)
    app.register_blueprint(blog_bp)
    return app
