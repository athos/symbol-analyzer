(defproject symbol-analyzer "0.1.1-SNAPSHOT"
  :description "Clojure code analyzer that tells us how each symbol is being used in the code"
  :url "https://github.com/athos/symbol-analyzer"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojars.trptcolin/sjacket "0.1.4" :exclusions [[org.clojure/clojure]]]
                 [org.clojure/core.match "0.2.2"]]
  :profiles {:1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :dev {:dependencies [[org.clojure/tools.namespace "0.2.10"]]
                   :source-paths ["dev"]}}
  :aliases {"all" ["with-profile" "dev:1.5:1.6"]}
  :lein-release {:deploy-via :clojars})
