(ns symbol-analyzer.extraction
  (:refer-clojure :exclude [extend])
  (:require [clojure.core.match :refer [match]]
            [symbol-analyzer.utils :as utils]))

(def ^:private ^:dynamic *symbol-key*)

(defn- get-id [x]
  (get (meta x) *symbol-key*))

;;
;; Environment
;;
(defn- make-env [locals]
  {:locals locals})

(defn- lookup [env n]
  (or ((:locals env) n)
      (resolve n)))

(defn- extend [env n]
  (assoc-in env [:locals n] (if-let [id (get-id n)] id :none)))

(defn- extend-with-seq [env seq]
  (reduce extend env seq))

;;
;; Extraction
;;
(def ^:private specials
  '#{def if do quote set! try catch finally throw var monitor-enter monitor-exit
     new . let* fn* loop* recur letfn* case* reify* deftype* clojure.core/import*})

(defn- special? [[op]]
  (boolean (specials op)))

(defn- extract-from-symbol [env sym]
  (or (when-let [m (get-id sym)]
        (let [e (lookup env sym)]
          (cond (var? e) {m {:type :var :usage :ref :var e}}
                (class? e) {m {:type :class :class e}}
                e {m {:type :local :usage :ref :binding e}}
                :else nil)))
      {}))

(defn- assoc-if-marked-symbol [map sym val]
  (if-let [id (get-id sym)]
    (assoc map id val)
    map))

(defn- assoc-each [map val seq]
  (reduce #(assoc-if-marked-symbol %1 %2 (if (fn? val) (val %2) val)) map seq))

(declare extract*)

(defn- extract-from-forms [env forms]
  (into {} (map #(extract* env %) forms)))

(defmulti ^:private extract-from-special (fn [env [op]] op))
(defmethod extract-from-special :default [env [op & args]]
  (-> {}
      (assoc-if-marked-symbol op {:type :special :op op})
      (merge (extract-from-forms env args))))

(defn- extract-from-seq [env [maybe-op :as seq]]
  (cond (special? seq)
        #_=> (extract-from-special env seq)
        (symbol? maybe-op)
        #_=> (let [e (lookup env maybe-op)]
               (if (or (var? e) (nil? e))
                 ;; op may be a macro or .method or Class. or Class/method

                 ;; FIXME: transform from original interop form to dot
                 ;; special form using macroexpand is a little bit kludgy
                 ;; way, and in some corner cases it shouldn't work
                 (let [expanded (macroexpand seq)]
                   (cond (= expanded seq)
                         #_=> (extract-from-forms env seq)
                         (var? e)
                         #_=> (-> {}
                                  (assoc-if-marked-symbol maybe-op {:type :macro :macro e})
                                  (merge (extract* env expanded)))
                         :else (extract* env expanded)))
                 (extract-from-forms env seq)))
        :else (extract-from-forms env seq)))

(defn- extract* [env form]
  (cond (and (instance? clojure.lang.IObj form)
             (meta form) (not (::extracted (meta form))))
        #_=> (merge (extract* env (meta form))
                    (extract* env (utils/add-meta form {::extracted true})))
        (symbol? form)
        #_=> (extract-from-symbol env form)
        (seq? form)
        #_=> (extract-from-seq env form)
        (vector? form)
        #_=> (extract-from-forms env form)
        (map? form)
        #_=> (merge (extract-from-forms env (keys form))
                    (extract-from-forms env (vals form)))
        (set? form)
        #_=> (extract-from-forms env form)
        :else {}))

(defn extract [form & {:keys [ns locals symbol-key]
                       :or {ns *ns*, locals nil, symbol-key :id}}]
  (binding [*symbol-key* symbol-key]
    (let [locals (into {} (for [name locals] [name :user-specified]))]
      (binding [*ns* ns]
        (doall (extract* (make-env locals) form))))))

;;
;; Implementation of etraction methods
;; for each special form (related to bindings)
;;

(defmacro def-special-extractor [op & clauses]
  `(defmethod extract-from-special '~op [~'env ~'form]
     (match ~'form
       ~@(->> (for [[pat maybe-map maybe-expr] clauses
                    :let [map (if (map? maybe-map) maybe-map {})
                          expr (if (map? maybe-map) maybe-expr maybe-map)]]
                 [`(~(vec pat) :seq)
                  `(let [op# (first ~'form)]
                     (-> {}
                         (assoc-if-marked-symbol op# {:type :special :op op#})
                         ~@(for [[name usage] map]
                             `(cond-> (symbol? ~name)
                                      (assoc-if-marked-symbol ~name ~usage)))
                         (merge ~expr)))])
              (apply concat))
       :else nil)))

(defn- collect-symbols [ret x]
  (cond (symbol? x) (conj ret x)
        (map? x) (-> ret
                     (collect-symbols (keys x))
                     (collect-symbols (vals x)))
        (coll? x) (reduce collect-symbols ret x)
        :else ret))

(def-special-extractor quote
  [(_ sexp)
   (assoc-each {} {:type :quoted} (collect-symbols [] sexp))])

(def-special-extractor def
  [(_ name)
   {name {:type :var :usage :def :name name}}
   (extract* env (meta name))]
  [(_ name expr)
   {name {:type :var :usage :def :name name}}
   (merge (extract* env (meta name))
          (extract* env expr))])

(defn- extract-from-bindings [env bindings]
  (loop [env env, [[name expr] & more :as bindings] (partition 2 bindings), ret {}]
    (if (empty? bindings)
      [ret env]
      (recur (extend env name)
             more
             (merge (assoc-if-marked-symbol ret name {:type :local :usage :def})
                    (extract* env (meta name))
                    (extract* env expr))))))

(def-special-extractor let*
  [(_ bindings & body)
   (let [[info env] (extract-from-bindings env bindings)]
     (merge info (extract-from-forms env body)))])

(def-special-extractor loop*
  [(_ bindings & body)
   (let [[info env] (extract-from-bindings env bindings)]
     (merge info (extract-from-forms env body)))])

(defn- extract-from-args [env args]
  (loop [env env, [name & more :as args] args, ret {}]
    (cond (empty? args) [ret env]
          (= name '&) (recur env more ret)
          :else (recur (extend env name)
                       more
                       (->> {:type :local :usage :def}
                            (assoc-if-marked-symbol ret name)
                            (merge (extract* env (meta name))))))))

(defn- extract-from-clauses [env clauses]
  (->> (for [[args & body] clauses
             :let [[info env] (extract-from-args env args)]]
         (merge info
                (extract* env (meta args))
                (extract-from-forms env (map meta args))
                (extract-from-forms env body)))
       (into {})))

(def-special-extractor fn*
  [(_ (args :guard vector?) & body)
   (extract-from-special env `(fn* (~args ~@body)))]
  [(_ (fname :guard symbol?) (args :guard vector?) & body)
   (extract-from-special env `(fn* fname (~args ~@body)))]
  [(_ (clause :guard seq?) & clauses)
   (extract-from-special env `(fn* nil ~clause ~@clauses))]
  [(_ fname & clauses)
   {fname {:type :local :usage :def}}
   (extract-from-clauses (if fname (extend env fname) env) clauses)])

(defn- extract-from-letfn-bindings [env bindings]
  (let [bindings' (partition 2 bindings)
        fnames (map first bindings')
        fns (map second bindings')
        env' (extend-with-seq env fnames)
        info (assoc-each {} {:type :local :usage :def} fnames)]
    [(reduce #(merge %1 (extract* env' %2)) info fns)
     env']))

(def-special-extractor letfn*
  [(_ bindings & body)
   (let [[info env] (extract-from-letfn-bindings env bindings)]
     (merge info (extract-from-forms env body)))])

(def-special-extractor catch
  [(_ class name & body)
   {class {:type :class :class (lookup env class)}
    name {:type :local :usage :def}}
   (extract-from-forms (extend env name) body)])

(def-special-extractor new
  [(_ class & args)
   {class {:type :class :class (lookup env class)}}
   (extract-from-forms env args)])

(def-special-extractor .
  [(_ class-or-obj field-or-method & args)
   {field-or-method {:type :member :name field-or-method}}
   (merge (extract* env class-or-obj)
          (extract-from-forms env args))])

(defn- extract-from-case-map [env map]
  (->> (for [[_ [test then]] map]
         (-> (extract* env then)
             (cond-> (symbol? test)
                     (assoc-if-marked-symbol test {:type :quoted})
                     (coll? test)
                     (assoc-each {:type :quoted} (collect-symbols [] test)))))
       (into {})))

(def-special-extractor case*
  [(_ expr shift mask default case-map table-type test-type & skip-check)
   (merge (extract* env expr)
          (extract* env default)
          (extract-from-case-map env case-map))])

(defn- extract-from-methods [env methods]
  (->> (for [[mname args & body] methods]
         (merge (extract* env (meta mname))
                (assoc-if-marked-symbol {} mname {:type :member :name mname})
                (extract-from-forms env (map meta args))
                (assoc-each {} {:type :local :usage :def} args)
                (extract-from-forms (extend-with-seq env args) body)))
       (into {})))

(def-special-extractor reify*
  [(_ interfaces & methods)
   (merge (extract-from-forms env interfaces)
          (extract-from-methods env methods))])

(def-special-extractor deftype*
  [(_ tagname classname fields :implements interfaces & methods)
   {tagname {:type :class :class (lookup env tagname)}
    classname {:type :class :class (lookup env classname)}}
   (-> (extract-from-methods (extend-with-seq env fields) methods)
       (assoc-each (fn [field] {:type :field :name field}) fields)
       (assoc-each (fn [if] {:type :class :class (lookup env if)}) interfaces))])
