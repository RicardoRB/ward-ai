from app.main import app


def test_violation() -> None:
    assert app is None
