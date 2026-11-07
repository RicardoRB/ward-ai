import os


def used_helper():
    return os.name


def dead_helper():
    return 1


@staticmethod
def decorated_is_skipped():
    return 2


def __private_dunder__():
    return 3


def test_discovered_by_pytest():
    return used_helper()


def main():
    return used_helper()


if __name__ == "__main__":
    main()
