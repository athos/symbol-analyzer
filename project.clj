(defproject symbol-analyzer "0.1.0-SNAPSHOT"
  :description "Clojure code analyzer that tells us how each symbol is being used in the code"
  :url "https://github.com/athos/symbol-analyzer"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojars.trptcolin/sjacket "0.1.4" :exclusions [[org.clojure/clojure]]]
                 [org.clojure/core.match "0.2.1"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.5"]]
                   :source-paths ["src" "dev"]}})
