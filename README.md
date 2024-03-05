# Template Processing for Clojure

This library provides equivalent functionality to
`java.lang.StringTemplate`.

## Dependency Info

```clojure
io.github.bowbahdoe {:git/sha ""}
```

## How to Use

Templates are initialized using the `<<` macro. Each
call to `<<` needs a template processor which determines
how the template will be processed and a string literal
that will be scanned for replacements.

```clojure
=> (require '[dev.mccue.template-processor 
              :as template-processor
              :refer [<<]])
nil
=> (def name "bobbie")
#'name
=> (<< template-processor/str "Hello ~{name}")
"Hello bobbie"
=> (<< template-processor/str "Len: ~(.length name)")
"Len: 6"
```

`~{}` is used for substituting constant values, `~()` for full expressions.