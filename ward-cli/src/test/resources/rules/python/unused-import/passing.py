from __future__ import annotations

import hashlib
import json
import os
from collections import OrderedDict as OrderedDict
from typing import TYPE_CHECKING, Optional

if TYPE_CHECKING:
    from models import Y

__all__ = ["encode", "hashlib"]

cache: dict[str, "Y"] = {}


def encode(path: os.PathLike, item: "Y") -> "Optional[str]":
    return json.dumps(os.fspath(path))
