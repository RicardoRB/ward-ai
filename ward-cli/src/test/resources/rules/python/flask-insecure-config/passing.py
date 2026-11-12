import os


def configure(app):
    app.config["SESSION_COOKIE_SECURE"] = True
    app.secret_key = os.environ["FLASK_SECRET_KEY"]
    app.config.update(SESSION_COOKIE_HTTPONLY=True)
