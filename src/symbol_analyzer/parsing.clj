(ns symbol-analyzer.parsing
  (:require [net.cgrand.parsley :as p]
            [net.cgrand.sjacket.parser :as s]))

(def ^:constant +root-tag+ ::s/root)

(def new-id
  (let [n (atom 0)]
    (fn []
      (swap! n inc)
      @n)))

(def ^:private parse*
  (letfn [(make-node [tag content]
            (let [node (p/->Node tag content)]
              (if (= tag :symbol)
                (assoc node :id (new-id))
                node)))]
    (p/make-parser {:main :sexpr*
                    :space [s/space-nodes :*]
                    :root-tag +root-tag+
                    :make-node make-node
                    }
                   s/rules)))

(defn parse [s]
  (parse* s))

(defn node-tag [node]
  (:tag node))

(defn node-content* [node]
  (:content node))

(defn node-content [node]
  (first (node-content* node)))

(defn unfinished? [node]
  (= (:tag node) :net.cgrand.parsley/unfinished))

(defn unexpected? [node]
  (some #(= (:tag %) :net.cgrand.parsley/unexpected)
        (tree-seq node-tag node-content* node)))
