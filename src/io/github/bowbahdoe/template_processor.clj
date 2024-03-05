(ns io.github.bowbahdoe.template-processor
  (:require [clojure.string :as string])
  (:import (clojure.lang IFn IPersistentMap))
  (:refer-clojure :exclude [str]))

(set! *warn-on-reflection* true)

(defn- silent-read
  "Attempts to clojure.core/read a single form from the provided String, returning
  a vector containing the read form and a String containing the unread remainder
  of the provided String. Returns nil if no valid form can be read from the
  head of the String."
  [s]
  (try
    (let [r (-> s java.io.StringReader. java.io.PushbackReader.)]
      [(read r) (slurp r)])
    (catch Exception e))) ; this indicates an invalid form -- the head of s is just string data

(defn- interpolate
  "Yields a seq of Strings and read forms."
  ([s atom?]
   (lazy-seq
     (if-let [[form rest] (silent-read (subs s (if atom? 2 1)))]
       (cons form (interpolate (if atom? (subs rest 1) rest)))
       (cons (subs s 0 2) (interpolate (subs s 2))))))
  ([^String s]
   (if-let [start (->> ["~{" "~("]
                      (map #(.indexOf s ^String %))
                      (remove #(== -1 %))
                      sort
                      first)]
     (lazy-seq (cons
                 (subs s 0 start)
                 (interpolate (subs s start) (= \{ (.charAt s (inc start))))))
     [s])))

(defn- produce-string-template
  [template-string]
  (let [interpolated (interpolate template-string)]
    {:fragments (vec (take-nth 2 interpolated))
     :values    (vec (take-nth 2 (rest interpolated)))}))

(defprotocol StringTemplate
  "A protocol-ized version of java.lang.StringTemplate. Default implementations
exist for instances of that class and for maps.

On maps, it will use the values in the :fragments and :values keys for implementing
the methods."
  (fragments [_]
    "A not-empty vector of string fragments.
Should always be one element larger than the list returned by values.")
  (values [_]
    "A vector of values to substitute between the fragments."))

(extend-protocol StringTemplate
  java.lang.StringTemplate
  (fragments [this]
    (vec (.fragments this)))
  (values [this]
    (vec (.values this)))

  IPersistentMap
  (fragments [this]
    (:fragments this))
  (values [this]
    (:values this)))

(defprotocol Processor
  "Protocol for a template processor"
  (process [_ string-template]
    "Processes a string template, producing some result.

The kind of result produced depends on the processor."))

(extend-protocol Processor
  java.lang.StringTemplate$Processor
  (process [this string-template]
    (.process this (reify java.lang.StringTemplate
                     (fragments [_] (fragments string-template))
                     (values [_] (values string-template)))))
  IFn
  (process [this string-template]
    (this string-template)))

(defn str
  "Template processor which joins elements using clojure.core/str.

Behavior differs from StringTemplate/STR in the same way java.lang.Objects/toString differs
from clojure.core/str."
  [template]
  (let [fragments (fragments template)]
    (apply clojure.core/str
           (cons (first fragments)
                 (interleave (values template) (rest fragments))))))

(defn sqlvec
  "Template processor which produces SQLVec.

SQLVec consists of a vector where the first element is a string of SQL containing
placeholders and the following elements are the values that should be substituted
into those placeholders.

Any values are substituted in the template with a single ?. Sequential values are unrolled
into multiple placeholders and multiple elements in the resulting vector."
  [template]
  (let [fragments (fragments template)
        values    (values template)
        sql-str   (apply clojure.core/str
                         (cons (first fragments)
                               (interleave (map (fn [v]
                                                  (if (sequential? v)
                                                    (clojure.core/str
                                                      "("
                                                      (string/join ","
                                                                   (repeat (count v) "?"))
                                                      ")")
                                                    "?"))
                                                values)
                                           (rest fragments))))]
    (vec (cons sql-str
               (mapcat (fn [v]
                         (if (sequential? v)
                           v
                           [v]))
                       values)))))

(defmacro <<
  "Takes a template processor and a string literal, interprets the string literal as
a StringTemplate, and processes that template with the template processor.

the string data and evaluated expressions contained within that argument.
Values can be substituted using ~{} and ~() forms. The former is used for
simple values; the latter can be used to
embed the results of arbitrary function invocation into the produced StringTemplate.

Examples:
user=> (require '[dev.mccue.template-processor :as template-processor :refer [<<]])
nil
user=> (def v 30.5)
#'user/v
user=> (<< template-processor/str \"This trial required ~{v}ml of solution.\")
\"This trial required 30.5ml of solution.\"
user=> (<< template-processor/str \"There are ~(int v) days in November.\")
\"There are 30 days in November.\"
user=> (def m {:a [1 2 3]})
#'user/m
user=> (<< template-processor/str \"The total for your order is $~(->> m :a (apply +)).\")
\"The total for your order is $6.\"
user=> (<< template-processor/sqlvec \"SELECT * FROM table WHERE val=~{v}\")\n
[\"SELECT * FROM table WHERE val=?\" 30.5]

Note that quotes surrounding string literals within ~() forms must be
escaped."
  [processor template-string]
  (when-not (string? template-string)
    (throw (RuntimeException. "Template String must be string literal")))
  `(let [template# ~(produce-string-template template-string)]
     (process ~processor template#)))