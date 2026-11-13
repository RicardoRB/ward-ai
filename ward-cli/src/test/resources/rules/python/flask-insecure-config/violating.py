def configure(app):
    app.config["SESSION_COOKIE_SECURE"] = False
    app.secret_key = "dev"
    app.config.update(SESSION_COOKIE_HTTPONLY=False)
