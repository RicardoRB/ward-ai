from django import forms
from django.contrib import admin


class SignupForm(forms.Form):
    name = forms.CharField()

    def clean_name(self):
        return self.cleaned_data["name"].strip()


class SizeFilter(admin.SimpleListFilter):
    title = "size"

    def lookups(self, request, model_admin):
        return (("big", "Big"),)
