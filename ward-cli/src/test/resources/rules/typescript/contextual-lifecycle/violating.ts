import { Component, Injectable, NgModule } from '@angular/core';

@Component({ selector: 'app-a', template: '' })
export class AComponent {
  ngOnInit(): void {}
  ngDoBootstrap(): void {}
}

@Injectable()
export class AService {
  ngOnInit(): void {}
  ngOnDestroy(): void {}
}
