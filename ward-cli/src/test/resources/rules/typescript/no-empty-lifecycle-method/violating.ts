import { Component } from '@angular/core';

@Component({ selector: 'app-b', template: '' })
export class BComponent {
  ngOnInit(): void {}
  ngOnDestroy() {
    this.cleanup();
  }
  private cleanup(): void {}
}
