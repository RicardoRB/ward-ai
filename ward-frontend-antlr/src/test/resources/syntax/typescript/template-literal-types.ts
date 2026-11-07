type Greeting = `hello ${string}`;
type Ev<Name extends string> = `on${Capitalize<Name>}`;
