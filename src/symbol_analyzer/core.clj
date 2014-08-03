(ns symbol-analyzer.core
  (:require [symbol-analyzer.conversion :refer [convert]]
            [symbol-analyzer.extraction :refer [extract]])
  (:import net.cgrand.parsley.Node))

(defn- postwalk-nodes [f node]
  (if (instance? Node node)
    (let [{:keys [content]} node
          node' (assoc node :content (mapv #(postwalk-nodes f %) content))]
      (f node'))
    node))

(defn- annotate [node info]
  (-> (fn [node]
        (if-let [usage (and (= (:tag node) :symbol) (info (:id node)))]
          (assoc node :usage usage)
          node))
      (postwalk-nodes node)))

;;
;; Entry point
;;
(defn analyze [root & {:keys [ns suppress-eval?]}]
  (let [ns (or ns *ns*)
        sexps (convert root :ns ns :symbol-key :id)
        ext (fn [info sexp]
              (when-not suppress-eval?
                (binding [*ns* ns]
                  (eval sexp)))
              (merge info (extract sexp :ns ns :symbol-key :id)))
        info (reduce ext {} sexps)]
    (annotate root info)))
