from fastapi.testclient import TestClient

from app.main import app


def test_app_serves_the_count_route() -> None:
    assert TestClient(app).get("/orders/count").json() == {"count": 0}
