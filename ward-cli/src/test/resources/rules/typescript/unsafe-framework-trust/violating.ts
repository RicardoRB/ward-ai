export class View {
  html(html) {
    return this.sanitizer.bypassSecurityTrustHtml(html);
  }

  url(address) {
    return this.sanitizer.bypassSecurityTrustResourceUrl(address);
  }
}

export class Styles {
  style(color: string) {
    return this.sanitizer.bypassSecurityTrustStyle('color: ' + color);
  }
}
