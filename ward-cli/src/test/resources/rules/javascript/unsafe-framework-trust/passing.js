export class View {
  html() {
    return this.sanitizer.bypassSecurityTrustHtml('<b>fixed</b>');
  }

  clean(html) {
    return this.sanitizer.sanitize(1, html);
  }
}
