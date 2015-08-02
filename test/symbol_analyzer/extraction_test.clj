(ns symbol-analyzer.extraction-test
  (:require [symbol-analyzer.extraction :refer :all]
            [clojure.test :refer :all]
            [clojure.core.match :refer [match]]))

(defn- install-data-readers [n]
  (letfn [(make-annotator [id]
            (fn [form]
              (vary-meta form assoc :id id)))]
    (dotimes [i n]
      (alter-var-root #'default-data-readers assoc (symbol (str '$ i)) (make-annotator i)))))

(install-data-readers 16)

(defmacro extracted [form expected]
  (let [v (gensym 'v)]
    `(let [~v (extract '~form :ns *ns* :symbol-id-key :id)]
       (and ~@(for [[id info] expected]
                `(match (get ~v ~id)
                   ~info true
                   :else false))))))

(deftest extract-from-symbol-test
  (is (extracted 
        #$0 cons 
        {0 {:type :var}}))
  (is (extracted 
        #$0 clojure.lang.IFn
        {0 {:type :class :class (_ :guard #(= % clojure.lang.IFn))}}))
  (is (extracted
        #$0 String
        {0 {:type :class :class (_ :guard #(= % java.lang.String))}})))

(deftest extract-from-collection-test
  (is (extracted
        [#$0 cons [#$1 cons]]
        {0 {:type :var}}))
  (is (extracted
        {#$0 key #$1 val}
        {0 {:type :var}, 1 {:type :var}}))
  (is (extracted
        #{#$0 map}
        {0 {:type :var}})))

(deftest extract-from-application-test
  (is (extracted
        (#$0 map #$1 identity [1 2 3])
        {0 {:type :var}, 1 {:type :var}}))
  (is (extracted
        (#$0 filter (#$1 complement #$2 even?) (#$3 range 10))
        {0 {:type :var}, 1 {:type :var}, 2 {:type :var}, 3 {:type :var}}))
  (is (extracted
        ((#$0 constantly 0) 1)
        {0 {:type :var}})))

(deftest extract-from-if-test
  (is (extracted
        (#$0 if (#$1 not true)
          (#$2 println 'yes)
          (#$3 println 'no))
        {0 {:type :special}, 1 {:type :var}, 2 {:type :var}, 3 {:type :var}})))

(deftest extract-from-do-test
  (is (extracted
        (#$0 do (#$1 println 'foo)
          (#$2 println 'bar))
        {0 {:type :special}, 1 {:type :var}, 2 {:type :var}})))

(deftest extract-from-quote-test
  (is (extracted
        '(#$0 foo (#$1 bar))
        {0 {:type :quoted}, 1 {:type :quoted}}))
  (is (extracted
        '(#$0 let [#$1 x 2] (#$2 * #$3 x 3))
        {0 {:type :quoted}, 1 {:type :quoted},
         2 {:type :quoted}, 3 {:type :quoted}}))
  (is (extracted
        '[#$0 foo [#$1 bar]]
        {0 {:type :quoted}, 1 {:type :quoted}}))
  (is (extracted
        '{#$0 foo {#$1 bar #$2 baz}}
        {0 {:type :quoted}, 1 {:type :quoted}, 2 {:type :quoted}}))
  (is (extracted
        '#{#$0 foo #{#$1 bar}}
        {0 {:type :quoted}, 1 {:type :quoted}})))

(deftest extract-from-def-test
  (is (extracted
        (#$0 def #$1 foo)
        {0 {:type :special}, 1 {:type :var}}))
  (is (extracted
        (#$0 def #$1 foo (#$2 inc 0))
        {0 {:type :special}, 1 {:type :var}, 2 {:type :var}}))
  #_(is (extracted
        (def f (fn [] #$0 f))
        {0 {:type :var}})))

(deftest extract-from-let-test
  (is (extracted
        (#$0 let [#$1 x (#$2 inc 0)
                  #$3 y (#$4 * #$5 x 2)]
          (#$6 * #$7 y 2))
        {0 {:type :macro}, 1 {:type :local}, 2 {:type :var}, 3 {:type :local}
         4 {:type :var}, 5 {:type :local}, 6 {:type :var}, 7 {:type :local}})))

(deftest extract-from-loop-recur-test
  (is (extracted
        (#$0 loop [#$1 x (#$2 inc 0)
                   #$3 y (#$4 * #$5 x 2)]
          (#$6 recur (#$7 inc #$8 x) (#$9 * #$10 y 2)))
        {0 {:type :macro}, 1 {:type :local}, 2 {:type :var}, 3 {:type :local}
         4 {:type :var}, 5 {:type :local}, 6 {:type :special}, 7 {:type :var}
         8 {:type :local}, 9 {:type :var}, 10 {:type :local}})))

(deftest extract-from-fn-test
  (is (extracted
        (#$0 fn [#$1 x] (#$2 * #$3 x 2))
        {0 {:type :macro}, 1 {:type :local}, 2 {:type :var}, 3 {:type :local}}))
  (is (extracted
        (fn [#$0 x #$1 & #$2 y] [#$3 x #$4 y])
        {0 {:type :local}, 1 nil, 2 {:type :local}, 3 {:type :local},
         4 {:type :local}}))
  (is (extracted
        (fn [[#$0 x #$1 & #$2 y]] [#$3 x #$4 y])
        {0 {:type :local}, 1 nil, 2 {:type :local}, 3 {:type :local},
         4 {:type :local}}))
  (is (extracted
        (fn #$0 f [#$1 x] (#$2 f #$3 x))
        {0 {:type :local}, 1 {:type :local}, 2 {:type :local}, 3 {:type :local}}))
  (is (extracted
        (fn ([#$0 x] (* #$1 x 2))
          ([#$2 x #$3 y] (* #$4 x #$5 y)))
        {0 {:type :local}, 1 {:type :local}, 2 {:type :local}, 3 {:type :local}
         4 {:type :local}, 5 {:type :local}}))
  (is (extracted
        (fn #$0 f
          ([#$1 x] (#$2 f #$3 x 2))
          ([#$4 x #$5 y] (#$6 f #$7 x #$8 y)))
        {0 {:type :local}, 1 {:type :local}, 2 {:type :local}, 3 {:type :local}
         4 {:type :local}, 5 {:type :local}, 6 {:type :local}, 7 {:type :local}
         8 {:type :local}})))

(deftest extract-from-letfn-test
  (is (extracted
        (#$0 letfn [(#$1 f [#$2 x] (#$3 g #$4 x))
                    (#$5 g [#$6 x] (#$7 f #$8 x))]
             (#$9 f (#$10 g (#$11 inc 0))))
        {0 {:type :macro}, 1 {:type :local}, 2 {:type :local}, 3 {:type :local}
         4 {:type :local}, 5 {:type :local}, 6 {:type :local}, 7 {:type :local}
         8 {:type :local}, 9 {:type :local}, 10 {:type :local}, 11 {:type :var}})))

(deftest extract-from-set!-test
  (is (extracted
        (#$0 set! #$1 *warn-on-reflection* (#$2 not false))
        {0 {:type :special}, 1 {:type :var}, 2 {:type :var}})))

(deftest extract-from-var-test
  (is (extracted
        #'#$0 cons
        {0 {:type :var}}))
  (is (extracted
        #'#$0 no-such-var-found
        {0 nil})))

(deftest extract-from-throw-test
  (is (extracted
        (#$0 throw (#$1 ex-info "error!" {}))
        {0 {:type :special}, 1 {:type :var}})))

(deftest extract-from-try-catch-finally-test
  (is (extracted
        (#$0 try
          (#$1 println "foo")
          (#$2 catch #$3 IllegalStateException #$4 e
            (#$5 println #$6 e))
          (#$7 catch #$8 Exception #$9 e
            (#$10 println #$11 e))
          (#$12 finally
            (#$13 println "bar")))
        {0 {:type :special}, 1 {:type :var}, 2 {:type :special}, 3 {:type :class}
         4 {:type :local}, 5 {:type :var}, 6 {:type :local}, 7 {:type :special}
         8 {:type :class}, 9 {:type :local}, 10 {:type :var}, 11 {:type :local}
         12 {:type :special}, 13 {:type :var}})))

(deftest extract-from-monitor-enter-exit-test
  (is (extracted
        (let [o (new Object)]
          (try
            (#$0 monitor-enter #$1 o)
            (finally
              (#$2 monitor-exit #$3 o))))
        {0 {:type :special}, 1 {:type :local}, 2 {:type :special}, 3 {:type :local}})))

(deftest extract-from-import-test
  (is (extracted
        (#$0 import '#$1 java.io.Reader)
        {0 {:type :macro}, 1 nil})))

(deftest extract-from-new-test
  (is (extracted
        (#$0 new #$1 Integer (#$2 inc 0))
        {0 {:type :special}, 1 {:type :class}, 2 {:type :var}})))

(deftest extract-from-dot-test
  (is (extracted
        (#$0 . #$1 System #$2 out)
        {0 {:type :special}, 1 {:type :class}, 2 {:type :member}}))
  (is (extracted
        (let [s "foo"]
          (. #$0 s #$1 substring 0 (#$2 inc 0)))
        {0 {:type :local}, 1 {:type :member}, 2 {:type :var}}))
  #_(is (extracted
        (let [Integer "foo"]
          (. #$0 Integer #$1 valueOf 1))
        {0 {:type :class}, 1 {:type :member}})))

(deftest extract-from-case-test
  (is (extracted
        (#$0 case '#$1 bar
          #$2 foo (#$3 inc 0)
          (#$4 bar #$5 baz) (#$6 dec 0)
          (#$7 * 2 2))
        {0 {:type :macro}, 1 {:type :quoted}, 2 {:type :quoted},
         3 {:type :var}, 4 {:type :quoted}, 5 {:type :quoted},
         6 {:type :var}, 7 {:type :var}}))
  (is (extracted
        (case '[foo bar]
          [#$0 foo #$1 bar] :vector
          {#$2 foo #$3 bar} :map
          #{#$4 foo #$5 bar} :set)
        {0 {:type :quoted}, 1 {:type :quoted}, 2 {:type :quoted},
         3 {:type :quoted}, 4 {:type :quoted}, 5 {:type :quoted}})))

(deftest extract-from-reify-test
  (is (extracted
        (#$0 reify
          #$1 Runnable
          (#$2 run [#$3 this] (#$4 println #$5 this))
          #$6 clojure.lang.IFn
          (#$7 invoke [#$8 this #$9 x] (#$10 inc #$11 x)))
        {0 {:type :macro}, 1 {:type :class}, 2 {:type :member}, 3 {:type :local}
         4 {:type :var}, 5 {:type :local}, 6 {:type :class}, 7 {:type :member}
         8 {:type :local}, 9 {:type :local}, 10 {:type :var}, 11 {:type :local}})))

;; kludge for extraction from deftype form without errors
(in-ns 'user)
(deftype T [x y])

(in-ns 'symbol-analyzer.extraction-test)

(deftest extract-from-deftype-test
  (is (extracted
        (#$0 deftype #$1 T [#$2 x #$3 y]
             #$4 Runnable
             (#$5 run [#$6 this] (#$7 println #$8 this #$9 x))
             #$10 clojure.lang.IFn
             (#$11 invoke [#$12 this #$13 x] [#$14 x #$15 y]))
        {0 {:type :macro}, 1 {:type :class}, 2 {:type :field}, 3 {:type :field}
         4 {:type :class}, 5 {:type :member}, 6 {:type :local}, 7 {:type :var}
         8 {:type :local}, 9 {:type :local}, 10 {:type :class}, 11 {:type :member}
         12 {:type :local}, 13 {:type :local}, 14 {:type :local}, 15 {:type :local}})))

(deftest extract-from-meta-test
  (is (extracted
        ^ #$0 String x
        {0 {:type :class :class (_ :guard #(= % java.lang.String))}}))
  (is (extracted
        ^{:tag #$0 String} x
        {0 {:type :class :class (_ :guard #(= % java.lang.String))}}))
  (is (extracted
        (def ^#$0 String x)
        {0 {:type :class :class (_ :guard #(= % java.lang.String))}}))
  (is (extracted
        (def ^#$0 String x "hoge")
        {0 {:type :class :class (_ :guard #(= % java.lang.String))}}))
  (is (extracted
        (let [^#$0 String x "hoge"] x)
        {0 {:type :class :class (_ :guard #(= % java.lang.String))}}))
  (is (extracted
        (loop [^#$0 String x "hoge"] x)
        {0 {:type :class :class (_ :guard #(= % java.lang.String))}}))
  (is (extracted
        (fn ^#$0 String [^#$1 String x] x)
        {0 {:type :class :class (_ :guard #(= % java.lang.String))}
         1 {:type :class :class (_ :guard #(= % java.lang.String))}}))
  (is (extracted
        (reify
          clojure.lang.IFn
          (^#$0 Object invoke [this ^#$1 Object x] ^#$2 Object x))
        {0 {:type :class :class (_ :guard #(= % java.lang.Object))}
         1 {:type :class :class (_ :guard #(= % java.lang.Object))}
         2 {:type :class :class (_ :guard #(= % java.lang.Object))}})))
