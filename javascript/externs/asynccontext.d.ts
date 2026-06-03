/**
 * @fileoverview The spec of the AsyncContext API.
 * @see https://github.com/tc39/proposal-async-context/
 */

declare namespace AsyncContext {
  export class Variable<T> {
    constructor(opts?: {name?: string; defaultValue?: T});
    readonly name: string;
    get(): T;
    run<R, A extends unknown[]>(value: T, fn: (...args: A) => R, ...args: A): R;
  }
  export class Snapshot {
    static wrap<F extends Function>(fn: F): F;
    run<R, A extends unknown[]>(fn: (...args: A) => R, ...args: A): R;
  }
}
