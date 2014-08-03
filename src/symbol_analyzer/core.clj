(ns symbol-analyzer.core
  (:require [clojure.walk :refer [postwalk]]
            [symbol-analyzer.conversion :refer [convert]]
            [symbol-analyzer.extraction :refer [extract]])
  (:import net.cgrand.parsley.Node))

;;
;; Annotation
;;
(defn- annotate [node info]
  (if (string? node)
    node
    (let [{:keys [content]} node
          node' (assoc node :content (mapv #(annotate % info) content))]
      (if-let [usage (and (= (:tag node) :symbol) (info (:id node)))]
        (assoc node' :usage usage)
        node'))))

;;
;; Entry point
;;
(defn analyze [root & {:keys [ns suppress-eval?]}]
  (let [ns (or ns *ns*)
        sexps (convert root :symbol-key :id)
        ext (fn [info sexp]
              (when-not suppress-eval?
                (binding [*ns* ns]
                  (eval sexp)))
              (merge info (extract sexp :ns ns :symbol-key :id)))
        info (reduce ext {} sexps)]
    (annotate root info)))
