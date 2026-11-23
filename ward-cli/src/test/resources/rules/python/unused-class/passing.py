__all__ = ["Exported"]


class Exported:
    pass


class Helper:
    pass


value = Helper()


class EmailBackend:
    pass


AUTHENTICATION_BACKENDS = ["app.backends.EmailBackend"]


class ApiConfig(AppConfig):
    name = "hc.api"
