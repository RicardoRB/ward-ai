from pathlib import Path

BASE_DIR: Path = Path(__file__).resolve().parent.parent
SECRET_KEY: str = "matrix-only-not-a-secret"
DEBUG: bool = False
ALLOWED_HOSTS: list[str] = ["localhost"]
INSTALLED_APPS: list[str] = [
    "django.contrib.contenttypes",
    "django.contrib.auth",
    "orders",
    "billing",
]
MIDDLEWARE: list[str] = []
ROOT_URLCONF: str = "mysite.urls"
DATABASES: dict[str, dict[str, object]] = {
    "default": {
        "ENGINE": "django.db.backends.sqlite3",
        "NAME": BASE_DIR / "db.sqlite3",
    }
}
DEFAULT_AUTO_FIELD: str = "django.db.models.BigAutoField"
