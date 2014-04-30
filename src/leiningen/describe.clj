(ns leiningen.describe
  (:require [leiningen.describe.reports.text :as text]))

(defn as-text [project]
  (str
   (text/display-project-dependencies project)
   "\n\n"
   (text/display-plugin-dependencies project)))

(defn describe
  "Display information about project dependencies."
  [project & args]
  (println (as-text project)))
