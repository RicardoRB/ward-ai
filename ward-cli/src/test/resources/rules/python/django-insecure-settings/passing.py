import os

DEBUG = False
SECRET_KEY = os.environ["DJANGO_SECRET_KEY"]
ALLOWED_HOSTS = ["app.example.com"]
SESSION_COOKIE_SECURE = True
