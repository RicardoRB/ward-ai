from __future__ import annotations

import hashlib
import json
import os
from collections import OrderedDict as OrderedDict

__all__ = ["encode", "hashlib"]


def encode(path: os.PathLike) -> str:
    return json.dumps(os.fspath(path))
