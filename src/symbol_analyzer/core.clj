(ns symbol-analyzer.core
  (:require [clojure.walk :refer [postwalk]]
            [symbol-analyzer.conversion :refer [convert]]
            [symbol-analyzer.extraction :refer [extract]])
  (:import net.cgrand.parsley.Node))

(def ^:private new-id
  (let [n (atom 0)]
    (fn []
      (swap! n inc)
      @n)))

(defn- add-meta [x m]
  (if (meta x)
    (vary-meta x conj m)
    (with-meta x m)))

(defn- mark-sexp [sexp]
  (-> (fn [t]
        (if (symbol? t)
          (add-meta t {:id (new-id)})
          t))
      (postwalk sexp)))

(defn- annotate-sexp [sexp info]
  (-> (fn [t]
        (if-let [usage (and (symbol? t) (info (:id (meta t))))]
          (add-meta t {:usage usage})
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
          (assoc node :id (new-id))
          node))
      (postwalk-nodes node)))

(defn- annotate [node info]
  (-> (fn [node]
        (if-let [usage (and (= (:tag node) :symbol) (info (:id node)))]
          (assoc node :usage usage)
          node))
      (postwalk-nodes node)))

;;
;; Entry points
;;

(defn analyze-sexp [sexp & {:keys [ns]}]
  (let [ns (or ns *ns*)
        sexp (mark-sexp sexp)
        info (extract sexp :ns ns :symbol-key :id)]
    (annotate-sexp sexp info)))

(defn analyze [root & {:keys [ns suppress-eval?]}]
  (let [ns (or ns *ns*)
        root (mark root)
        sexps (convert root :ns ns :symbol-key :id)
        ext (fn [info sexp]
              (when-not suppress-eval?
                (binding [*ns* ns]
                  (eval sexp)))
              (merge info (extract sexp :ns ns :symbol-key :id)))
        info (reduce ext {} sexps)]
    (annotate root info)))
