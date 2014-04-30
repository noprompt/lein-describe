(ns leiningen.describe.reports.text
  (:require [leiningen.core.classpath :as classpath]
            [leiningen.core.main :as main]
            [leiningen.core.user :as user]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.pprint :refer [pprint pp]]
            [leiningen.describe.dependencies]))

(defn coordinates->string [data]
  (format "Dependency: [%s %s]"
          (:name data)
          (pr-str (:version data))))

(defn transitive-coordinates->string [data]
  (format "[%s %s]"
          (:name data)
          (pr-str (:version data))))

(defn description->string [data]
  (let [d (or (:description data) "none")]
    (str "Description: " (string/trim d))))

(defn url->string [data]
  (str "URL: " (or (:url data) "none")))

(defn licenses->string [data]
  (let [ls (:licenses data)
        delimiter "\n            "]
    (str "License(s): " (if (seq ls)
                          (string/join delimiter ls)
                          "none"))))

(defn transitive-deps->string [data]
  (let [ds (:dependencies data)
        delimiter "\n              "]
    (str "Dependencies: "
         (if (seq ds)
           (string/join delimiter
                        (for [d ds
                              :when (not= "test" (:scope d))]
                          (transitive-coordinates->string d)))
           "none"))))

(def ^:private data->strings
  (juxt coordinates->string
        description->string
        url->string
        licenses->string
        transitive-deps->string))

(defn- lines-for-dependencies [deps-file-map]
  (let [all-deps-data (dependency-map deps-file-map)]
    (string/join
     "\n\n"
     (for [data all-deps-data]
       (if (map? data)
         (string/join "\n" (data->strings data))
         data)))))

(def ^:private separator
  (str "\n" (apply str (repeat 72 "-")) "\n"))

(defn display-project-dependencies [project]
  (let [project-deps (get-project-dependencies project)]
    (str "PROJECT DEPENDENCIES:"
         separator
         (if (seq project-deps)
           (lines-for-dependencies project-deps)
           "This project has no dependencies."))))

(defn display-plugin-dependencies [project]
  (let [plugin-deps (get-plugin-dependencies project)]
    (str "PLUGIN DEPENDENCIES:"
         separator
         (if (seq plugin-deps)
               (lines-for-dependencies plugin-deps)
               "This project has no plugins."))))
