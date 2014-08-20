(ns symbol-analyzer.conversion-test
  (:require [symbol-analyzer.conversion :refer :all]
            [net.cgrand.sjacket.parser :as p]
            [clojure.test :refer :all]
            [clojure.core.match :refer [match]]))

(defn- parse-and-convert=read-string [code]
  (= (first (convert (p/parser code)))
     (read-string code)))

(defmacro parse-and-convert-matches-pattern [code pattern & conditions]
  `(match (first (convert (p/parser ~code)))
     ~pattern
     (and ~@conditions)
     :else false))

(deftest convert-to-nil
  (is (parse-and-convert=read-string "nil")))

(deftest convert-to-boolean
  (is (parse-and-convert=read-string "true"))
  (is (parse-and-convert=read-string "false")))

(deftest convert-to-symbol
  (is (parse-and-convert=read-string "foo"))
  (is (parse-and-convert=read-string "foo/bar")))

(deftest convert-to-keyword
  (is (parse-and-convert=read-string ":foo-bar"))
  (is (parse-and-convert=read-string ":foo/bar"))
  (is (binding [*ns* (the-ns 'user)]
        (parse-and-convert=read-string "::foo-bar"))))

(deftest convert-to-number
  (is (parse-and-convert=read-string "42"))
  (is (parse-and-convert=read-string "42N"))
  (is (parse-and-convert=read-string "42.23"))
  (is (parse-and-convert=read-string "42.23M"))
  (is (parse-and-convert=read-string "4/2")))

(deftest convert-to-char
  (is (parse-and-convert=read-string "\\f"))
  (is (parse-and-convert=read-string "\\u0194"))
  (is (parse-and-convert=read-string "\\o123"))
  (is (parse-and-convert=read-string "\\newline")))

(deftest convert-to-string
  (is (parse-and-convert=read-string "\"foo bar\""))
  (is (parse-and-convert=read-string "\"foo\\\\bar\""))
  (is (parse-and-convert=read-string "\"foo\\000bar\""))
  (is (parse-and-convert=read-string "\"foo\\u0194bar\""))
  (is (parse-and-convert=read-string "\"foo\\123bar\"")))

(deftest convert-to-regex
  (is (str (first (convert (p/parser "#\"\\[\\]?(\\\")\\\\\""))))
      (str (read-string "#\"\\[\\]?(\\\")\\\\\""))))

(deftest convert-to-fn
  (is (parse-and-convert-matches-pattern "#(apply % %1 %3 %&)"
        (['fn* [p1 p2 p3 '& p&] (['apply x x1 x3 x&] :seq)] :seq)
        (and (= p1 x x1) (= p3 x3) (= p& x&)
             (not= p1 p2) (not= p3 p2) (not= p& p2))))
  (is (parse-and-convert-matches-pattern "#(quote %)"
        (['fn* [p] (['quote x] :seq)] :seq)
        (= p x)))
  (is (parse-and-convert-matches-pattern "#(let [% 0] %)"
        (['fn* [p] (['let [x 0] x'] :seq)] :seq)
        (= p x x'))))

(deftest convert-to-meta
  (is (parse-and-convert=read-string "^:key foo-bar"))
  (is (parse-and-convert=read-string "^type foo-bar"))
  (is (parse-and-convert=read-string "^\"type\" foo-bar"))
  (is (parse-and-convert=read-string "^{:key 0} foo-bar")))

(deftest convert-to-var
  (is (parse-and-convert=read-string "#'foo"))
  (is (parse-and-convert=read-string "#'foo/bar")))

(deftest convert-to-deref
  (is (parse-and-convert=read-string "@foo")))

(deftest convert-to-quote
  (is (parse-and-convert=read-string "'foo"))
  (is (parse-and-convert=read-string "'(1 2 3)")))

(deftest convert-to-syntax-quote
  (is (parse-and-convert=read-string "`1"))
  (is (parse-and-convert=read-string "`if"))
  (is (parse-and-convert=read-string "`cons"))
  (is (parse-and-convert=read-string "`x"))
  (is (parse-and-convert=read-string "`clojure.core/cons"))
  (is (parse-and-convert-matches-pattern "(`(x# x#) `x#)"
        ([(['clojure.core/seq
            (['clojure.core/concat
              (['clojure.core/list (['quote (x1 :guard symbol?)] :seq)] :seq)
              (['clojure.core/list (['quote (x2 :guard symbol?)] :seq)] :seq)] :seq)] :seq)
          (['quote (x3 :guard symbol?)] :seq)] :seq)
        (and (= x1 x2) (not= x1 x3))))
  (is (parse-and-convert=read-string "`Class"))
  (is (parse-and-convert=read-string "`java.lang.Class"))
  (is (parse-and-convert=read-string "`.member"))
  (is (parse-and-convert=read-string "`Class."))
  (is (parse-and-convert=read-string "`(x (y) z)"))
  (is (parse-and-convert=read-string "`[x [y] z]"))
  (is (parse-and-convert=read-string "`{x {y z} v w}"))
  (is (parse-and-convert=read-string "`#{x #{y} z}"))
  (is (parse-and-convert=read-string "`(x ~y z)"))
  (is (parse-and-convert=read-string "`(x ~'y z)"))
  (is (parse-and-convert=read-string "`(x ~@y z)"))
  (is (parse-and-convert=read-string "`(x ~@[] z)"))
  (is (parse-and-convert=read-string "``x"))
  (is (parse-and-convert=read-string "``(~x ~~y z)"))
  (is (parse-and-convert=read-string "``(~x ~~@y z)")))

(deftest convert-to-unquote
  (is (parse-and-convert=read-string "~foo")))

(deftest convert-to-unquote-splicing
  (is (parse-and-convert=read-string "~@foo")))

(deftest convert-to-eval)

(deftest convert-to-reader-literal)

(deftest convert-to-list
  (is (parse-and-convert=read-string "()"))
  (is (parse-and-convert=read-string "(foo bar)"))
  (is (parse-and-convert=read-string "(foo (bar) baz)")))

(deftest convert-to-vector
  (is (parse-and-convert=read-string "[]"))
  (is (parse-and-convert=read-string "[foo bar]"))
  (is (parse-and-convert=read-string "[foo [bar] baz]")))

(deftest convert-to-map
  (is (parse-and-convert=read-string "{}"))
  (is (parse-and-convert=read-string "{foo bar}"))
  (is (parse-and-convert=read-string "{foo {bar baz}}")))

(deftest convert-to-set
  (is (parse-and-convert=read-string "#{}"))
  (is (parse-and-convert=read-string "#{foo bar}"))
  (is (parse-and-convert=read-string "#{foo #{bar} baz}")))
