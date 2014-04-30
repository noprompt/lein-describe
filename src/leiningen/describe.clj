(ns leiningen.describe
  (:require [leiningen.describe.reports.text :as text]))

(defn as-text [project]
  (str
   (text/deps->string project :dependencies)
   "\n\n"
   (text/deps->string project :plugins)))

(defn describe
  "Display information about project dependencies."
  [project & args]
  (println (as-text project)))
