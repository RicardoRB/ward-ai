from django.db import models


class Order(models.Model):
    """An order with a quantity."""
    quantity = models.IntegerField()
