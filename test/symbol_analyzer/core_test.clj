(ns symbol-analyzer.core-test
  (:require [clojure.test :refer :all]
            [symbol-analyzer.core :refer :all]
            [net.cgrand.sjacket.parser :refer [parser]]))

(ns A)
(def x nil)

(defn f [x]
  (print "evaled!")
  x)

(ns B)
(def x nil)

(in-ns 'symbol-analyzer.core-test)

(deftest analyze-sexp-test
  (binding [*ns* (the-ns 'A)]
    (testing "analyze-sexp with no options"
      (let [{:keys [type var]} (-> (analyze-sexp 'x) meta :symbol-info)]
        (is (= type :var))
        (is (= var #'A/x)))
      (is (number? (-> (analyze-sexp 'x) meta :id))))
    (testing "analyze-sexp :ns option"
      (let [{:keys [type var]} (-> (analyze-sexp 'x :ns 'B) meta :symbol-info)]
        (is (= type :var))
        (is (= var #'B/x))))
    (testing "analyze-sexp with :locals option"
      (is (= (-> (analyze-sexp 'x :locals '#{x}) meta :symbol-info :type)
             :local)))
    (testing "analyze-sexp :symbol-id-key option"
      (is (number? (-> (analyze-sexp 'x :symbol-id-key :id-key) meta :id-key))))
    (testing "analyze-sexp :symbol-info-key option"
      (is (= (-> (analyze-sexp 'x :symbol-info-key :info) meta :info :type)
             :var)))))

(deftest analyze-test
  (let [ast (parser "x")]
    (binding [*ns* (the-ns 'A)]
      (testing "analyze with no options"
        (let [{:keys [type var]} (-> (analyze ast) :content first :symbol-info)]
          (is (= type :var))
          (is (= var #'A/x)))
        (is (number? (-> (analyze ast) :content first :id))))
      (testing "analyze with :ns option"
        (let [{:keys [type var]} (-> (analyze ast :ns 'B) :content first :symbol-info)]
          (is (= type :var))
          (is (= var #'B/x))))
      (testing "analyze with :symbol-id-key option"
        (is (number? (-> (analyze ast :symbol-id-key :id-key) :content first :id-key))))
      (testing "analyze with :symbol-info-key option"
        (is (= (-> (analyze ast :symbol-info-key :info) :content first :info :type)
               :var)))
      (testing "analyze with :suppress-eval? option"
        (let [code (parser "(f nil) 0")]
          (is (= (with-out-str
                   (analyze code))
                 "evaled!"))
          (is (= (with-out-str
                   (analyze code :suppress-eval? true))
                 "")))))))
