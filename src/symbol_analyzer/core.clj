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
        (if-let [usage (and (symbol? t)
                            (info (get (meta t) *symbol-id-key*)))]
          (utils/add-meta t {*symbol-info-key* usage})
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
        (if-let [usage (and (= (:tag node) :symbol)
                            (info (get node *symbol-id-key*)))]
          (assoc node :symbol-info-key usage)
          node))
      (postwalk-nodes node)))

;;
;; Entry points
;;

(defn analyze-sexp [sexp & {:keys [ns symbol-id-key symbol-info-key]
                            :or {ns *ns*, symbol-id-key :id,
                                 symbol-info-key :symbol-info}}]
  (binding [*symbol-id-key* symbol-id-key
            *symbol-info-key* symbol-info-key]
    (let [sexp (mark-sexp sexp)
          info (extract sexp :ns ns :symbol-key symbol-id-key)]
      (annotate-sexp sexp info))))

(defn analyze [root & {:keys [ns symbol-id-key symbol-info-key suppress-eval?]
                       :or {ns *ns*, symbol-id-key :id,
                            symbol-info-key :symbol-info}}]
  (binding [*symbol-id-key* symbol-id-key
            *symbol-info-key* symbol-info-key]
    (let [root (mark root)
          sexps (convert root :ns ns :symbol-key symbol-id-key)
          ext (fn [info sexp]
                (when-not suppress-eval?
                  (binding [*ns* ns]
                    (eval sexp)))
                (merge info (extract sexp :ns ns :symbol-key symbol-id-key)))
          info (reduce ext {} sexps)]
      (annotate root info))))
