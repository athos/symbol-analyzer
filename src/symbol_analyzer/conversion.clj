(ns symbol-analyzer.conversion
  (:require [net.cgrand.sjacket.parser :as p])
  (:import net.cgrand.parsley.Node))

(defn- node-tag [node]
  (:tag node))

(defn- node-content* [node]
  (:content node))

(defn- node-content [node]
  (first (node-content* node)))

(def ^:private ^:dynamic *conv-ns*)
(def ^:private ^:dynamic *symbol-key*)

(defn get-id [x]
  (get (meta x) *symbol-key*))

(defn- remove-whitespaces [content]
  (filterv #(or (not (instance? Node %))
                (not (#{:whitespace :newline :comment :discard} (node-tag %))))
           content))

(defn- essential-content [x]
  (remove-whitespaces (node-content* x)))

(defmulti ^:private convert* node-tag)

(defmethod convert* ::p/root [x]
  (mapv convert* (essential-content x)))

(defmethod convert* :nil [x]
  nil)

(defmethod convert* :boolean [x]
  ({"true" true, "false" false} (node-content x)))

(defmethod convert* :symbol [x]
  (let [[maybe-ns _ maybe-name] (node-content* x)
        sym (if maybe-name
              (symbol (node-content maybe-ns) (node-content maybe-name))
              (symbol (node-content maybe-ns)))]
    (with-meta sym
      {*symbol-key* (:id x)})))

(defmethod convert* :keyword [x]
  (let [[colon maybe-ns _ maybe-name] (node-content* x)]
    (cond maybe-name
          #_=> (keyword (node-content maybe-ns) (node-content maybe-name))
          (= colon "::")
          #_=> (keyword (name (ns-name *ns*)) (node-content maybe-ns))
          :else (keyword (node-content maybe-ns)))))

(defmethod convert* :number [x]
  (read-string (node-content x)))

(defmethod convert* :char [x]
  (read-string (node-content x)))

(defmethod convert* :string [x]
  (read-string (apply str (node-content* x))))

(defmethod convert* :regex [x]
  (let [[_ _ s _] (node-content* x)]
    (re-pattern s)))

(defmethod convert* :fn [x])

(defmethod convert* :meta [x]
  (let [[_ meta-node form-node] (essential-content x)
        meta (convert* meta-node)
        meta (cond (keyword? meta) {meta true}
                   (or (symbol? meta)
                       (string? meta))
                   #_=> {:tag meta}
                   (map? meta) meta
                   ; FIXME: otherwise throw an exception
                   )]
    (vary-meta (convert* form-node) conj meta)))

(defmethod convert* :var [x]
  (let [[_ maybe-ns _ maybe-name] (essential-content x)
        sym (if maybe-name
              (symbol (convert* maybe-ns) (node-content maybe-name))
              (symbol (convert* maybe-ns)))]
    (list 'var sym)))

(defn- wrap [sym node]
  (let [[_ v] (essential-content node)]
    (list sym (convert* v))))

(defmethod convert* :deref [x]
  (wrap 'clojure.core/deref x))

(defmethod convert* :quote [x]
  (wrap 'quote x))

(defmethod convert* :syntax-quote [x])

(defmethod convert* :unquote [x]
  (wrap 'clojure.core/unquote x))

(defmethod convert* :unquote-splicing [x]
  (wrap 'clojure.core/unquote-splicing x))

(defmethod convert* :eval [x])

(defmethod convert* :reader-literal [x])

(declare convert-seq)

(defmethod convert* :list [x]
  (doall (convert-seq x)))

(defmethod convert* :vector [x]
  (vec (convert-seq x)))

(defmethod convert* :map [x]
  (into {} (map vec (partition 2 (convert-seq x)))))

(defmethod convert* :set [x]
  (set (convert-seq x)))

(defn convert [root & {:keys [ns symbol-key]}]
  (binding [*conv-ns* (the-ns (or ns *ns*))
            *symbol-key* (or symbol-key :id)]
    (convert* root)))

(defn- convert-seq [x]
  (->> (essential-content x)
       butlast
       rest
       (map convert*)))
