export class View {
  html(html) {
    return this.sanitizer.bypassSecurityTrustHtml(html);
  }

  url(address) {
    return this.sanitizer.bypassSecurityTrustResourceUrl(address);
  }
}
