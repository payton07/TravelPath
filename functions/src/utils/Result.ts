/**
 * Result monad — type-safe error handling without exceptions.
 *
 * Replaces throw/catch chains with an explicit success/failure type.
 * Every function that can fail returns Result<T> instead of throwing.
 *
 * Design pattern: Monad / Railway-Oriented Programming
 */
export type Result<T, E = Error> =
    | { readonly ok: true;  readonly value: T }
    | { readonly ok: false; readonly error: E };

export const Result = {
    ok:  <T>(value: T): Result<T, never>  => ({ ok: true,  value }),
    err: <E>(error: E): Result<never, E>  => ({ ok: false, error }),

    fromPromise: async <T>(p: Promise<T>): Promise<Result<T, Error>> => {
        try   { return Result.ok(await p); }
        catch (e) { return Result.err(e instanceof Error ? e : new Error(String(e))); }
    },

    map: <T, U, E>(result: Result<T, E>, fn: (v: T) => U): Result<U, E> =>
        result.ok ? Result.ok(fn(result.value)) : result,

    flatMap: <T, U, E>(result: Result<T, E>, fn: (v: T) => Result<U, E>): Result<U, E> =>
        result.ok ? fn(result.value) : result,
} as const;
