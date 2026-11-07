__all__ = ["exported"]


def exported():
    return 1


def helper():
    return exported()


class Service:
    def run(self):
        return helper()


Service().run()
