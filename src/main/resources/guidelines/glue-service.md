# Artifact: glueService

A Glue service exposes:

- `script.glue`: executable Glue source
- `input.xml` and `output.xml`: generated, read-only service contracts
- `resources/...`: editable auxiliary UTF-8 text resources
- `metadata.xml`: repository metadata

Invalid Glue syntax is rejected before `script.glue` is persisted. After changing the script, inspect `input.xml` and `output.xml` when its contract matters.

# Glue language reference

Glue is an indentation-sensitive, optionally typed scripting language running on the JVM. Use tabs consistently for blocks. Nabu services, Glue scripts, lambdas, core functions, and available Java methods are callable as functions.

## Values, variables, and types

```glue
name = "Ada"
integer count = "5"
string label ?= "default"
[] values ?= null
```

- `=` assigns, while `?=` declares an input and applies the right-hand default only when no value was supplied.
- Common types are `integer`, `decimal`, `boolean`, `string`, `date`, `bytes`, `uuid`, `uri`, and `lambda`. Nabu-defined and Java types can also be used.
- Types apply to that assignment and convert or validate the value; variables can be assigned with another type later.
- Prefix arbitrary-precision numeric literals with the `b` suffix, such as `5b` or `4.0b`.
- The left operand generally determines coercion for overloaded operators, so use explicit typing where coercion would be ambiguous.
- A final array input (`[] values ?= null`) acts as varargs.

## Field access and queries

Use `/`, not `.`, for fields and paths. Dot notation qualifies function namespaces.

```glue
person/firstName
person["firstName"]
people[age >= 18]/firstName
minimumAge = 18
people[age >= /minimumAge]
```

Bracket expressions select fields dynamically, access zero-based series indexes, or filter collections. A leading `/` inside a filter refers to the outer/root scope. Queries work with Nabu structures, Java beans, maps, XML, and JSON.

Direct mutation is supported where appropriate:

```glue
person/name = "Updated"
people[id == targetId]/active = true
```

## Operators

Standard operators include `+`, `-`, `*`, `/`, `%`, `**`, comparisons, `==`, `!=`, `!`, short-circuit `&&` and `||`, XOR `^`, regex match `~` and `!~`, membership `?` and `!?`, and lambda composition `°`. Operators are overloaded for types such as strings, dates, series, and lambdas.

## Calling functions and services

Every Nabu service is exposed directly as a Glue function using its fully qualified artifact id:

```glue
result = my.module.services.lookup(customerId: id)
```

A service call returns its complete Nabu output structure, including when the service has only one scalar output field. Select that field explicitly when assigning the scalar value:

```glue
date = nabu.utils.Date.now()/date
identifier = my.module.services.generateId()/identifier
```

Do not assume a single output is automatically unwrapped. Inspect the service's output contract or its `output.xml` fragment to determine the root field name.

Every structure known to the Nabu repository is also exposed as a Glue type by its fully qualified artifact id. It can be used for inputs, assignments, lambda parameters, and structural conversion:

```glue
my.module.types.Customer customer ?= null
my.module.types.Customer normalized = structure(customer, active: true)
```

Function namespaces are optional when names are unambiguous. For example, `generate(...)` and `series.generate(...)` are equivalent. Prefer fully qualified names for Nabu services and whenever overload resolution is unclear.

Calls accept positional and named parameters:

```glue
calculate(1, 2)
calculate(right: 2, left: 1)
substring(5, string: value)
```

Named arguments move the positional assignment cursor to that parameter. Any later unnamed argument continues after it, so avoid mixing forms unless this behavior is intentional. Named arguments require a runtime function definition; dynamic operating-system commands may not provide one.

## Script inputs and outputs

Top-level `?=` assignments define service input. Glue scripts and long lambdas return their pipeline by default. Mark explicit outputs with `@return`; Nabu Glue services should explicitly mark intended output fields.

```glue
integer left ?= null
integer right ?= 0

@return
sum = left + right
```

Multiple `@return` values produce a structure. Without explicit returns, callers receive the complete pipeline and access values with `/`.

## Control flow and errors

```glue
if (active)
	doWork()
else if (retry)
	retryWork()
else
	throw("work.failed")

for (item : items)
	echo(item)

while (pending)
	poll()
```

- `for (items)` exposes `$value` and `$index`; `for (item : items)` names the value.
- `for (3)` iterates values `0`, `1`, and `2`.
- `break` exits a control structure; `break 2` exits two nested structures.
- `switch(value)` uses `case(...)` and `default`; a parameterless `switch` behaves like an if/else chain.
- `sequence` groups steps. It supports `catch` and `finally`; the caught error is `$exception`.
- `try` also supports `catch`/`finally`, but an unhandled error exits the try and execution continues after it.
- Use `throw(message)` or `throw(code, parameters)`; inspect nested errors with `cause($exception)`.

## Lambdas, methods, and sequences

A short lambda treats every argument except the last as an input declaration:

```glue
sum = lambda(left: 0, right: 0, left + right)
sum(right: 2)
```

Use long form for control flow and explicit returns:

```glue
sum = lambda
	integer left ?= 0
	integer right ?= 0
	@return
	result = left + right
```

Lambdas capture an immutable snapshot of their enclosing scope. `method` uses the same syntax but shares mutable enclosing state; use `@persist` on values that must flow back to that shared scope. `sequence` is useful as a script-like callable/group when closure semantics are not needed.

Functions are first-class. `compose(first, second)` pipes left to right; `first ° second` follows mathematical right-to-left composition. `dispatch(...)` combines lambdas and selects by arity and convertible parameter types.

## Structures

Create dynamic structures with named fields:

```glue
customer = structure(id: id, name: name)
updated = structure(customer, name: "New name")
```

Structures can model script inputs and outputs. Passing a structure to a typed script performs structural compatibility checks and applies defaults for omitted optional fields. Explicit `null` remains null. Use `keys(value)` and bracket access for dynamic traversal.

## Lazy series

Series are generally lazy and may be infinite. Do not fully resolve, sort, reverse, or request the last item of an unbounded series.

Common functions:

- Construction: `series(...)`, `generate(lambda)`, `repeat(...)`, `range(...)`
- Bounds/access: `limit(count, values)`, `offset(count, values)`, `first(values)`, `last(values)`
- Transformation: `derive(lambda, series...)`, `explode(lambda, values...)`, `filter(lambda, values)`, `merge(...)`
- Reduction: `aggregate(lambda, values)`, `resolve(values)`, `sort(comparator, values)`
- Inspection: `size(values)`, `depth(values)`, `dimensions(values)`

```glue
numbers = generate(lambda(value: 0, value + 1))
evens = filter(lambda(value, value % 2 == 0), numbers)
firstTen = resolve(limit(10, evens))
```

Arithmetic and many core functions lift over series lazily. `derive` combines corresponding values from one or more series. `explode` maps one input to zero or more outputs. Negative `offset` removes values from the end and requires finite lookahead.

Laziness avoids unnecessary work, but repeatedly traversed or deeply nested lazy transformations can become slow because values and iterator chains are resolved on demand. Use `resolve(series)` deliberately to materialize a finite intermediate result when it will be reused, traversed repeatedly, nested in further derivations, or when profiling indicates lazy overhead. Apply `limit`, `until`, or another finite bound before resolving a potentially infinite series.

```glue
filtered = filter(lambda(item, item/active), source)
materialized = resolve(filtered)
result = resolve(derive(transform, materialized))
```

## Strings, regex, and templates

Useful string functions include `upper`, `lower`, `substring`, `replace`, `find`, `split`, `join`, `padLeft`, and `padRight`. Most accept a final series/varargs input and return corresponding results.

`replace` and `find` use Java regular expressions. Use `quoteRegex(value)` for literal patterns and `quoteReplacement(value)` for literal replacements. `replace` can accept a lambda replacement for per-match logic.

Templates support `${expression}` and `${{ embeddedGlue }}`. Calling `template(content)` uses the current pipeline; additional structures or values provide explicit template contexts.

## Dates and numbers

- `date()` returns now; `date(value)` parses common formats.
- `parse(format, timezone, language, date: values...)` and `format(...)` provide explicit conversion.
- Date `+`/`-` accepts milliseconds or strings such as `"1 day"`, `"30 minutes"`, and `"2 months"`.
- Math functions include `abs`, `ceil`, `floor`, `sqrt`, trigonometric functions, `log`, `random`, `pi`, and `e`.
- Big decimals default to DECIMAL128 behavior; `rounding(precision, mode)` changes the thread-local math context.

## Parallel work

`run(lambda, params...)` starts asynchronous work and returns a future. `wait(futures...)` returns results in input order; with no arguments it waits for all asynchronous actions. Future arguments passed to `run` are resolved before invocation. `abort(future)` requests cancellation; `abort()` aborts the current script. `until(condition, interval: ..., timeout: ...)` waits asynchronously for a condition.

## Metadata

- `# comment` documents source.
- `## description` describes executable behavior.
- `@name value` adds an annotation to the next statement.
- Metadata at the top followed by a blank line applies to the script itself; otherwise it applies to the following statement.

## Resources

Use logical fragment paths `resources/<name>`. The physical repository layout is hidden. In Glue, `resources()` lists attached names and `resource(name)` reads one. MCP treats these resources as UTF-8 text; do not use fragment editing for binary resources.
