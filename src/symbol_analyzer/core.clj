(ns symbol-analyzer.core
  (:require [clojure.walk :refer [postwalk]]
            [symbol-analyzer.conversion :refer [convert]]
            [symbol-analyzer.extraction :refer [extract]]
            [symbol-analyzer.utils :as utils])
  (:import net.cgrand.parsley.Node))

(def ^:private ^:dynamic *symbol-id-key*)
(def ^:private ^:dynamic *symbol-info-key*)

(def ^:private new-id
  (let [n (atom 0)]
    (fn []
      (swap! n inc)
      @n)))

(defn- mark-sexp [sexp]
  (-> (fn [t]
        (if (symbol? t)
          (utils/add-meta t {*symbol-id-key* (new-id)})
          t))
      (postwalk sexp)))

(defn- annotate-sexp [sexp info]
  (-> (fn [t]
        (if (symbol? t)
          (let [usage (or (info (get (meta t) *symbol-id-key*))
                          {:type :unknown})]
            (utils/add-meta t {*symbol-info-key* usage}))
          t))
      (postwalk sexp)))

(defn- postwalk-nodes [f node]
  (if (instance? Node node)
    (let [{:keys [content]} node
          node' (assoc node :content (mapv #(postwalk-nodes f %) content))]
      (f node'))
    node))

(defn- mark [node]
  (-> (fn [node]
        (if (= (:tag node) :symbol)
          (assoc node *symbol-id-key* (new-id))
          node))
      (postwalk-nodes node)))

(defn- annotate [node info]
  (-> (fn [node]
        (if (= (:tag node) :symbol)
          (let [usage (or (info (get node *symbol-id-key*))
                          {:type :unknown})]
            (assoc node *symbol-info-key* usage))
          node))
      (postwalk-nodes node)))

;;
;; Entry points
;;

(defn analyze-sexp [sexp & {:keys [ns locals symbol-id-key symbol-info-key]
                            :or {ns *ns*, locals nil, symbol-id-key :id,
                                 symbol-info-key :symbol-info}}]
  (binding [*symbol-id-key* symbol-id-key
            *symbol-info-key* symbol-info-key]
    (let [sexp (mark-sexp sexp)
          info (extract sexp :ns ns :locals locals :symbol-id-key symbol-id-key)]
      (annotate-sexp sexp info))))

(defn analyze [root & {:keys [ns symbol-id-key symbol-info-key suppress-eval?]
                       :or {ns *ns*, symbol-id-key :id,
                            symbol-info-key :symbol-info}}]
  (binding [*symbol-id-key* symbol-id-key
            *symbol-info-key* symbol-info-key]
    (let [root (mark root)
          sexps (convert root :ns ns :symbol-id-key symbol-id-key)
          loader (.getContextClassLoader (Thread/currentThread))
          ext (fn [info sexp]
                (when-not suppress-eval?
                  (binding [*ns* (the-ns ns)]
                    (eval sexp)))
                (with-bindings {clojure.lang.Compiler/LOADER loader}
                  (merge info
                         (extract sexp :ns ns :symbol-id-key symbol-id-key))))
          info (reduce ext {} sexps)]
      (annotate root info))))
