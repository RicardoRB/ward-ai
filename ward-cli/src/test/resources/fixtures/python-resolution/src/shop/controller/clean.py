from typing import TYPE_CHECKING

from django.db import models

from shop.price import total
from . import views
from ..repository.store import Store
from ..repository import store

if TYPE_CHECKING:
    from shop import price

try:
    import shop.repository.store as alternate
except ImportError:
    alternate = None


def load() -> object:
    from ..repository import store as deferred

    return deferred


def describe() -> str:
    return views.render() + str(total([1])) + str(Store) + str(store) + str(alternate)
