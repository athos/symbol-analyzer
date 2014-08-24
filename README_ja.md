# symbol-analyzer

[![Build Status](https://travis-ci.org/athos/symbol-analyzer.png)](https://travis-ci.org/athos/symbol-analyzer)

`symbol-analyzer`はClojureのコード中に含まれるシンボルの使われ方を解析するコード解析器です。Clojureコードの静的解析や、コードウォークが必要な複雑なマクロを定義するために使用できます。

## インストール

## 基本的な使い方

`symbol-analyzer`の基本的な使い方には、*エクストラクト(extract)*と*アナライズ(analyze)*の2通りがあります。

### エクストラクト(extract)

エクストラクトは、S式で表されたClojureコード中の指定されたシンボルの使われ方を解析し、その結果を返します。使われ方を解析する対象とするシンボルは、特定のキーを持つメタデータとして一意なIDを割り振ることにより指定します。デフォルトの設定では、このキーは`:id`になっています。

たとえば、`(let [x 0] x)`というコード中の2番目の`x`の使われ方は以下のように解析できます。

```clojure
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

解析するコード中でユーザが独自に定義したマクロが使われていても解析することができます。`symbol-analyzer`は解析の途中でマクロを展開するため、マクロ展開後の

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



## ライセンス

Copyright © 2014 OHTA Shogo

Distributed under the Eclipse Public License version 1.0.
