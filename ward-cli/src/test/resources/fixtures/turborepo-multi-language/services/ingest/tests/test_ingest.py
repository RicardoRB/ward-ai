from app.main import ingest


def test_ingest_returns_the_id() -> None:
    assert ingest({"id": "a"}) == "a"
