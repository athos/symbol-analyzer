# symbol-analyzer　[![Build Status](https://travis-ci.org/athos/symbol-analyzer.png)](https://travis-ci.org/athos/symbol-analyzer)

`symbol-analyzer`はClojureのコード中に含まれるシンボルの使われ方を解析するコード解析器です。Clojureコードの静的解析や、コードウォークが必要な複雑なマクロを定義するために使用できます。

## インストール

最新リリースバージョンは0.1.0です。

`symbol-analyzer`をインストールするには、以下を依存ライブラリとして追加して下さい。

```clojure
[symbol-analyzer　"0.1.0"]
```

## 基本的な使い方

`symbol-analyzer`の基本的な使い方には、*エクストラクト(extract)*と*アナライズ(analyze)*の2通りがあります。

**注意** `symbol-analyzer`は現在開発中であり、以下に説明するAPIは将来的にそのインタフェースや戻り値の形式が変更になる可能性があることに留意して下さい。

### エクストラクト(extract)

エクストラクトは、S式で表されたClojureコード中の指定されたシンボルの使われ方を解析し、その結果を返します(この結果のことを*シンボル情報*と呼びます)。使われ方を解析する対象とするシンボルは、特定のキーを持つメタデータとして一意なIDを割り振ることにより指定します。デフォルトの設定では、このキーは`:id`になっています。

たとえば、`(let [x 0] x)`というコード中の2番目の`x`の使われ方は以下のように解析できます。

```clojure
user=> (require '[symbol-analyzer.extraction :refer [extract]])
nil
user=> (extract '(let [x 0] ^{:id 0} x))
{0 {:type :local, :usage :ref, :binding :none}}
user=>
```

この例では、解析対象となる2番目の`x`にID `0`を割り振っています。実行結果から、ID `0`が割り振られたシンボル(つまり、今解析対象としている2番目の`x`)がローカル束縛の参照であることが分かります。他のシンボルに対してもIDを割り振ってみると、エクストラクトの結果は以下のようになります。

```clojure
user=> (extract '(^{:id 0} let [^{:id 1} x 0] ^{:id 2} x))
{2 {:type :local, :usage :ref, :binding 1}, 1 {:type :local, :usage :def}, 0 {:type :macro, :macro #'clojure.core/let}}
user=>
```

解析するコード中でユーザが独自に定義したマクロが使われている場合でも解析することができます。`symbol-analyzer`は、解析の過程でマクロが現れた場合には、マクロを含まないフォームになるまでマクロ展開を繰り返し、最終的な展開形からシンボルの使われ方を特定することができます。

```clojure
user=> (defmacro let1 [name expr & body] `(let [~name ~expr] ~@body))
#'user/let1
user=> (let1 x 2 (* x x))
4
user=> (extract '(let1 ^{:id 0} x 2 (* ^{:id 1} x x)))
{1 {:type :local, :usage :ref, :binding 0}, 0 {:type :local, :usage :def}}
user=>
```



### アナライズ(analyze)

アナライズは、コード中に含まれるすべてのシンボルを対象にエクストラクトを適用します。アナライズの結果、解析されたシンボル情報は入力コード中のシンボルにメタデータとして付加されます。

```clojure
user=> (require '[symbol-analyzer.core :refer [analyze-sexp]])
nil
user=> (set! *print-meta* true)    ; メタデータが表示されるようにするため
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

## ライセンス

Copyright © 2014-2015 OHTA Shogo

Distributed under the Eclipse Public License version 1.0.
