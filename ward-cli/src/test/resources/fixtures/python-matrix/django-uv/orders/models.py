from django.db import models


class Order(models.Model):
    quantity = models.IntegerField()
