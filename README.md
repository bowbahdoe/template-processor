# Template Processing for Clojure

This library provides equivalent functionality to
`java.lang.StringTemplate`.

**NOTE**: The underlying design of string templates seems [set to change](https://mail.openjdk.org/pipermail/amber-spec-experts/2024-March/004010.html)
and explicit template processors will probably go away. I'll try to update this once a JDK with those changes lands so the `<<` macro will still function once/if the `StringTemplate$Processor`
interface goes away.

## Dependency Info

```clojure
io.github.bowbahdoe/template-processor {:git/sha "90bf859354405b62e9e98cd3aaa4e412006bf566"}
```

## How to Use

Templates are initialized using the `<<` macro. Each
call to `<<` needs a template processor which determines
how the template will be processed and a string literal
that will be scanned for replacements.

```clojure
=> (require '[io.github.bowbahdoe.template-processor 
              :as template-processor
              :refer [<<]])
nil
=> (def name "bobbie")
#'name
=> (<< template-processor/str "Hello ~{name}")
"Hello bobbie"
=> (<< template-processor/str "Len: ~(.length name)")
"Len: 6"
=> (<< template-processor/sqlvec "SELECT * FROM table WHERE name=~{name}")
["SELECT * FROM table WHERE name=?" "bobbie"]
```

`~{}` is used for substituting constant values, `~()` for full expressions.
