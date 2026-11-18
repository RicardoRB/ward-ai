from django.contrib.auth import get_user_model


class Importer:
    def __init__(self):
        self.user = get_user_model()()

    def create(self):
        self.user.is_staff = True
        self.user.is_superuser = True
        return self.user
