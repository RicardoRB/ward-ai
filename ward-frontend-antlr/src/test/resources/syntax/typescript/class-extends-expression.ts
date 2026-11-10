class Plain extends Base {}
class Generic extends Base<string> {}
class Member extends namespace.Base {}
class FromExpression extends obj?.Base {}
class Parenthesized extends (obj?.Base) {}
