# symbol-analyzer　[![Build Status](https://travis-ci.org/athos/symbol-analyzer.png)](https://travis-ci.org/athos/symbol-analyzer)

`symbol-analyzer` is a code analyzer that analyzes for you how each symbol is being used in the code. It can be used in various ways such as static analysis for Clojure code or defining complicated macros that require code walk.

## Installation

## Basic Usage

The basic usages of `symbol-analyzer` are *extract* and *analyze*.


**Note** `symbol-analyzer` is still of alpha quality and its APIs and the format of their return values described below are highly subject to change.

### Extract

*Extract* analyzes how the specified symbols in the code are being used and will return the result, which we call *symbol information*. The target symbols to be analyzed are specified by assigning unique IDs as metadata with a specific key. The default key is `:id`.

For example, we can analyze the usage of the second `x` in `(let [x 0] x)` as follows:

```clojure
user=> (require '[symbol-analyzer.extraction :refer [extract]])
nil
user=> (extract '(let [x 0] ^{:id 0} x))
{0 {:type :local, :usage :ref, :binding :none}}
user=>
```

In this example, we assigned ID `0` to the second `x` we are targeting. From the result, we can find the symbol assigned ID `0` (i.e. the one we are targeting) to be an reference to an local binding. Similarly, we'll get the following result if we assign IDs to other symbols as well:

```clojure
user=> (extract '(^{:id 0} let [^{:id 1} x 0] ^{:id 2} x))
{2 {:type :local, :usage :ref, :binding 1}, 1 {:type :local, :usage :def}, 0 {:type :macro, :macro #'clojure.core/let}}
user=>
```

`symbol-analyzer` can even analyze code containing user-defined macros; the analyzer expands the macro by itself if it encounters a macro in the course of analysis, and it will identify the usage of symbols from the expanded form that doesn't contain macros.

```clojure
user=> (defmacro let1 [name expr & body] `(let [~name ~expr] ~@body))
#'user/let1
user=> (let1 x 2 (* x x))
4
user=> (extract '(let1 ^{:id 0} x 2 (* ^{:id 1} x x)))
{1 {:type :local, :usage :ref, :binding 0}, 0 {:type :local, :usage :def}}
user=>
```



### Analyze

*Analyze* applies *extract* to all the symbols in the code. Symbol information resulted from the extraction will be added to symbols in the input code as metadata.

```clojure
user=> (require '[symbol-analyzer.core :refer [analyze-sexp]])
nil
user=> (set! *print-meta* true)    ; to visualize metadata
nil
user=> (analyze-sexp '(let [x 0] x))
(^{:symbol-info {:type :macro, :macro #'clojure.core/let}, :id 7} let
 [^{:symbol-info {:type :local, :usage :def}, :id 8} x 0]
 ^{:symbol-info {:type :local, :usage :ref, :binding 8}, :id 9} x)
user=>
```

アナライズの結果を利用することで、ある種のコードウォーカーを非常に簡単に書けるようになります。たとえば、ローカル変数のみに対して何かしらの処理をしたい場合、通常はローカル環境を自前で管理しつつコードをトラバースしなければならず、その処理を書き上げるには多大な労力が必要です。一方、アナライズを利用すれば、`reduce`や`filter`といった簡単なシーケンス関数や`clojure.walk`を使うだけでそのようなコードウォーカーを実現することができます。以下の例では、`analyze-sexp`を使ってローカル変数を表すシンボルだけをリネームする関数を定義しています。

```clojure
user=> (defn rename-locals [sexp]
  #_=>   (postwalk (fn [x]
  #_=>               (if (and (symbol? x)
  #_=>                        (= (-> x meta :symbol-info :type) :local))
  #_=>                 (symbol (str \? x))
  #_=>                 x))
  #_=>             (analyze-sexp sexp)))
#'user/rename-locals
user=> (rename-locals '(let [x x] [x 'x]))
(let [?x x] [?x (quote x)])
user=>
```

入力コードに含まれる、自由変数としての`x`やクオートされたシンボルリテラルとしての`x`はリネームされず、`let`で束縛されたローカル変数の`x`のみが`?x`へリネームされていることを確認して下さい。

`symbol-analyzer`は、S式を入力としてとる`analyze-sexp`の他にも、[Sjacket](https://github.com/cgrand/sjacket)形式でパースされたClojureコードを入力としてとる`analyze`というAPIも提供しています。このインタフェースは、[genuine-highlighter](https://github.com/athos/genuine-highlighter)のようなシンタックスハイライター等、コードをテキストとして解析し、テキストとして書き戻すツールから利用されることを想定しています。

## License

Copyright © 2014 OHTA Shogo

Distributed under the Eclipse Public License version 1.0.
