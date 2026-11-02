from django.db import models


class Order(models.Model):
    reference = models.CharField(max_length=10)

    class Meta:
        ordering = ["missing"]
