from dataclasses import dataclass, field


@dataclass
class A:
    x: int = field(default=1)
