import { Component, Injectable, NgModule } from '@angular/core';

@Component({ selector: 'app-a', template: '' })
export class AComponent {
  ngOnInit(): void {}
  ngOnDestroy(): void {}
}

@Injectable()
export class AService {
  ngOnDestroy(): void {}
}

@NgModule({ declarations: [] })
export class AModule {
  ngDoBootstrap(): void {}
}

export class Plain {
  ngOnInit(): void {}
}
