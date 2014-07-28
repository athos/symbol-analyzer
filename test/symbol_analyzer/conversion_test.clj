(ns symbol-analyzer.conversion-test
  (:require [symbol-analyzer.conversion :refer :all]
            [symbol-analyzer.parsing :as p]
            [clojure.test :refer :all]))

(defn- parse-and-convert=read-string [code]
  (= (first (convert (p/parse code)))
     (read-string code)))

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
  (is (str (first (convert (p/parse "#\"\\[\\]?(\\\")\\\\\""))))
      (str (read-string "#\"\\[\\]?(\\\")\\\\\""))))

(deftest convert-to-fn)

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

(deftest convert-to-syntax-quote)

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
