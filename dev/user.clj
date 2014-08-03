(ns user
  (:require [clojure.repl :refer :all]
            [clojure.pprint :refer [pp pprint]]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [net.cgrand.sjacket.parser :as p]
            [symbol-analyzer [core :refer :all]
                             [extraction :as e]
                             [conversion :as c]]))

(def test-ast
  (p/parser "(let [x 0] (* x x))"))
