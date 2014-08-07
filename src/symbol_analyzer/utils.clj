(ns symbol-analyzer.utils)

(defn add-meta [x m]
  (vary-meta x merge m))
