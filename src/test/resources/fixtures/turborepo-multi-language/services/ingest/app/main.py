"""Ingest service entry point."""


def ingest(record: dict[str, str]) -> str:
    """Returns the identifier of an ingested record."""
    return record["id"]
