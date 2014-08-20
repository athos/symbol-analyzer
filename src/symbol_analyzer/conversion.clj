(ns symbol-analyzer.conversion
  (:require [net.cgrand.sjacket.parser :as p]
            [clojure.core.match :refer [match]]
            [symbol-analyzer.utils :as utils])
  (:import net.cgrand.parsley.Node
           [clojure.lang RT Namespace Var IObj IRecord]))

;;
;; Utilities
;;

(defn- copy-meta [x y]
  (utils/add-meta y (meta x)))

;;
;; Node manipulation
;;

(defn- node-tag [node]
  (:tag node))

(defn- node-content* [node]
  (:content node))

(defn- node-content [node]
  (first (node-content* node)))

(defn- remove-whitespaces [content]
  (filterv #(or (not (instance? Node %))
                (not (#{:whitespace :newline :comment :discard} (node-tag %))))
           content))

(defn- essential-content [x]
  (remove-whitespaces (node-content* x)))

;;
;; Conversion
;;

(def ^:private ^:dynamic *conv-ns*)
(def ^:private ^:dynamic *symbol-key*)

(defmulti ^:private convert* node-tag)

(defmethod convert* ::p/root [x]
  (mapv convert* (essential-content x)))

(defmethod convert* :nil [x]
  nil)

(defmethod convert* :boolean [x]
  ({"true" true, "false" false} (node-content x)))

(declare register-arg)

(defmethod convert* :symbol [x]
  (let [[maybe-ns _ maybe-name] (node-content* x)
        sym (if maybe-name
              (symbol (node-content maybe-ns) (node-content maybe-name))
              (symbol (node-content maybe-ns)))
        sym (-> (and (nil? (namespace sym))
                     (let [[s ^String n] (re-matches #"%([0-9]+|&)?" (name sym))]
                       (cond (nil? s) nil
                             (nil? n) 1
                             (= n "&") -1
                             :else (Long/parseLong n))))
                (some-> register-arg)
                (or sym))]
    (if-let [id (get x *symbol-key*)]
      (with-meta sym
        {*symbol-key* id})
      sym)))

(defn- resolve-ns [sym]
  (or ((ns-aliases *conv-ns*) sym)
      (find-ns sym)))

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

(def ^:private ^:dynamic arg-env)

(defn- garg [n]
  (symbol (str (if (== -1 n) "rest" (str "p" n))
               "__" (RT/nextID) "#")))

(declare convert-seq)

(defmethod convert* :fn [x]
  (when (thread-bound? #'arg-env)
    (throw (IllegalStateException. "Nested #()s are not allowed")))
  (binding [arg-env (sorted-map)]
    (let [form (doall (convert-seq x))
          rargs (rseq arg-env)
          args (if rargs
                 (let [higharg (inc (key (first rargs)))
                       args (mapv #(or (get arg-env %) (garg %))
                                  (range 1 higharg))]
                   (if (arg-env -1)
                     (conj args '& (arg-env -1))
                     args))
                 [])]
      (list 'fn* args form))))

(defn- register-arg [n]
  (when (thread-bound? #'arg-env)
    (or (arg-env n)
        (let [g (garg n)]
          (set! arg-env (assoc arg-env n g))
          g))))

(defmethod convert* :meta [x]
  (let [[_ meta-node form-node] (essential-content x)
        meta (convert* meta-node)
        meta (cond (keyword? meta) {meta true}
                   (or (symbol? meta)
                       (string? meta))
                   #_=> {:tag meta}
                   (map? meta) meta
                   ; FIXME: otherwise throw an exception
                   )
        form (convert* form-node)]
    (utils/add-meta form meta)))

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

(def ^:private ^:dynamic gensym-env)

(declare convert-syntax-quote)

(defn- unquote? [x]
  (and (seq? x) (= (first x) 'clojure.core/unquote)))

(defn- unquote-splicing? [x]
  (and (seq? x) (= (first x) 'clojure.core/unquote-splicing)))

(defn- expand-list [s]
  (letfn [(expand [x]
            (cond (unquote? x)
                  #_=> (list 'clojure.core/list (second x))
                  (unquote-splicing? x)
                  #_=> (second x)
                  :else (list 'clojure.core/list (convert-syntax-quote x))))]
    (doall (map expand s))))

(defn- flatten-map [m]
  (doall (apply concat m)))

(defn- register-gensym [sym]
  (when-not gensym-env
    (throw (IllegalStateException. "Gensym literal not in syntax-quote")))
  (or (get gensym-env sym)
      (let [gs (symbol (str (subs (name sym) 0 (dec (count (name sym))))
                            "__" (RT/nextID) "__auto__"))]
        (set! gensym-env (assoc gensym-env sym gs))
        gs)))

(defn- resolve-symbol [sym]
  (if (pos? (.indexOf (name sym) "."))
    sym
    (if-let [ns-str (namespace sym)]
      (let [^Namespace ns (resolve-ns (symbol ns-str))]
        (if (or (nil? ns) (= (name (ns-name ns)) ns-str))
          sym
          (symbol (name (.name ns)) (name sym))))
      (if-let [o ((ns-map *conv-ns*) sym)]
        (cond (class? o) (symbol (.getName ^Class o))
              (var? o) (symbol (-> ^Var o .ns .name name) (-> ^Var o .sym name)))
        (symbol (name (ns-name *ns*)) (name sym))))))

(defn- wrap-with-meta [form ret]
  (if (and (instance? IObj form)
           (dissoc (meta form) :line :column *symbol-key*))
    (list 'clojure.core/with-meta ret (convert-syntax-quote (meta form)))
    ret))

(defn- syntax-quote-coll [type coll]
  (let [res (list 'clojure.core/seq (cons 'clojure.core/concat (expand-list coll)))]
    (if type
      (list 'clojure.core/apply type res)
      res)))

(defn- convert-symbol-in-syntax-quote [sym]
  (->> (if (namespace sym)
         (let [maybe-class ((ns-map *conv-ns*) (symbol (namespace sym)))]
           (if (class? maybe-class)
             (symbol (.getName ^Class maybe-class) (name sym))
             (resolve-symbol sym)))
         (let [name (name sym)]
           (cond (.endsWith name "#")
                 #_=> (register-gensym sym)
                 (.startsWith name ".")
                 #_=> sym
                 (.endsWith name ".")
                 #_=> (let [csym (symbol (subs name 0 (dec (count name))))]
                        (symbol (str (resolve-symbol csym) ".")))
                 :else (resolve-symbol sym))))
      (copy-meta sym)
      (list 'quote)))

(defn- convert-coll-in-syntax-quote [coll]
  (cond (instance? IRecord coll) coll
        (map? coll) (syntax-quote-coll 'clojure.core/hash-map (flatten-map coll))
        (vector? coll) (syntax-quote-coll 'clojure.core/vector coll)
        (set? coll) (syntax-quote-coll 'clojure.core/hash-set coll)
        (or (seq? coll) (list? coll))
        #_=> (if-let [seq (seq coll)]
               (syntax-quote-coll nil seq)
               '(clojure.core/list))
        :else (throw (UnsupportedOperationException. "Unknown Collection type"))))

(defn- convert-syntax-quote [x]
  (->> (condp #(%1 %2) x
         special-symbol? (list 'quote x)
         symbol? (convert-symbol-in-syntax-quote x)
         unquote? (second x)
         unquote-splicing? (throw (IllegalStateException. "splice not in list"))
         coll? (convert-coll-in-syntax-quote x)
         keyword? x
         number? x
         char? x
         string? x
         (list 'quote x))
       (wrap-with-meta x)))

(defmethod convert* :syntax-quote [x]
  (binding [gensym-env {}]
    (let [[_ x'] (essential-content x)]
      (convert-syntax-quote (convert* x')))))

(defmethod convert* :unquote [x]
  (wrap 'clojure.core/unquote x))

(defmethod convert* :unquote-splicing [x]
  (wrap 'clojure.core/unquote-splicing x))

(defmethod convert* :eval [x])

(defmethod convert* :reader-literal [x])

(defmethod convert* :list [x]
  (doall (convert-seq x)))

(defmethod convert* :vector [x]
  (vec (convert-seq x)))

(defmethod convert* :map [x]
  (into {} (map vec (partition 2 (convert-seq x)))))

(defmethod convert* :set [x]
  (set (convert-seq x)))

(defn convert [root & {:keys [ns symbol-key]
                       :or {ns *ns*, symbol-key :id}}]
  (binding [*conv-ns* (the-ns ns)
            *symbol-key* symbol-key]
    (convert* root)))

(defn- convert-seq [x]
  (->> (essential-content x)
       butlast
       rest
       (map convert*)))
