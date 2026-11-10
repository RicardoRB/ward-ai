class Used:
    pass


class Dead:
    pass


class Registered(Used):
    pass


class __Dunder__:
    pass


@dataclass
class Decorated:
    pass


instance = Used()
