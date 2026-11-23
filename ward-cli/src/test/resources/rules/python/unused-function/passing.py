__all__ = ["exported"]


def exported():
    return 1


def helper():
    return exported()


class Service:
    def run(self):
        return helper()


Service().run()


def user_context(request):
    return {}


CONTEXT_PROCESSORS = ["app.context_processors.user_context"]
