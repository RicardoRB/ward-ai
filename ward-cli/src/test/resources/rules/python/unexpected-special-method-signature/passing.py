class Sized:
    def __len__(self):
        return 0


class Named:
    def __str__(self) -> str:
        return "named"

    def __eq__(self, other) -> bool:
        return other is self
