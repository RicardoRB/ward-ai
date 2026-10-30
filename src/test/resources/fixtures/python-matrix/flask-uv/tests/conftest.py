import pytest
from flask import Flask

from shop import create_app


@pytest.fixture
def app() -> Flask:
    return create_app()
