(ns leiningen.describe
  (:require [leiningen.core.classpath :as classpath]
            [leiningen.core.main :as main]
            [cemerick.pomegranate.aether :as aether]
            [clojure.java.io :as io]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.string :as string]
            [clojure.pprint :refer [pprint pp]])
  (:import (java.util.jar JarFile)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; POM data

;; TODO: This solution isn't complete and, currently, fails
;; often. Maybe xml-zip isn't the way to go?
(defn- normalize-xml* [xml-data]
  (loop [z (-> xml-data zip/xml-zip zip/down)]
    (if (zip/end? z)
      (:content (zip/root z))
      (-> z
          (zip/edit (fn [{:keys [tag content] :as node}]
                      (if (string? (first content))
                        {tag content}
                        {tag (normalize-xml* node)})))
          (zip/next)
          (recur)))))

(defn- normalize-xml
  "Return a map of {xml-tag-name content}."
  [xml-data]
  (reduce merge (normalize-xml* xml-data)))


(defn- get-pom-data [dep file]
  (let [group    (or (namespace dep) (name dep))
        artifact (name dep)
        pom-path (format "META-INF/maven/%s/%s/pom.xml" group artifact)
        jar      (JarFile. file)
        pom      (.getEntry jar pom-path)]
    (when pom
      (-> (.getInputStream jar pom)
          xml/parse))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Display helpers

(defn- dependency-name [data]
  (let [groupId (first (:groupId data))
        artifactId (first (data :artifactId))]
    (if (= groupId artifactId)
      groupId
      (str (symbol groupId artifactId)))))

(defn- dependency-version [data]
  (first (:version data)))

(defn- dependency-description [data]
  (first (:description data)))

(defn- dependency-url [data]
  (first (:url data)))

(defn- dependency-dependencies [data]
  (when-let [deps (seq (:dependencies data))]
    (for [dep deps
          :let [d (reduce merge (:dependency dep))
                n (dependency-name d)
                v (pr-str (dependency-version d))]]
      (format "[%s %s]" n v))))

(defn- dependency-licenses [data]
  (when-let [lics (seq (:licenses data))]
    (for [lic lics
          :let [l (reduce merge (:license lic))
                n (first (:name l))
                u (first (:url l))]]
      (format "%s (%s)" n u))))


(defn- dependency-line [data]
  (format "Dependency: [%s %s]"
          (dependency-name data)
          (pr-str (dependency-version data))))

(defn- description-line [data]
  (let [d (or (dependency-description data) "none")]
    (str "Description: " (string/trim d))))

(defn- url-line [data]
  (str "URL: " (or (dependency-url data) "none")))

(defn- dependencies-line [data]
  (let [ds (dependency-dependencies data)
        delimiter "\n              "]
    (str "Dependencies: " (if (seq ds)
                           (string/join delimiter ds)
                           "none"))))
(defn- licenses-line [data]
  (let [ls (dependency-licenses data)
        delimiter "\n            "]
    (str "License(s): " (if (seq ls)
                          (string/join delimiter ls)
                          "none"))))

(def ^:private lines*
  (juxt dependency-line
        description-line
        url-line
        licenses-line
        dependencies-line))

(defn- lines [data]
  (string/join "\n" (lines* data)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Dependency helpers 

(defn- get-project-dependencies
  "Return a map of {[dependency version & more] #<File /path/to.jar>}."
  [project]
  (as-> (classpath/get-dependencies :dependencies project) _
        (zipmap (keys _) (aether/dependency-files _))
        (select-keys _ (:dependencies project))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CLI 

(defn describe
  "Display information about project dependecies."
  [project & args]
  (let [deps (get-project-dependencies project)]
    (->>
     (for [[[dep version] file] deps
           :let [data (get-pom-data dep file)]
           :when (and data (not= "clojure" (name dep)))]
       (try 
         (lines (normalize-xml data))
         (catch Exception e
           (str (format "Error: There was a problem describing [%s %s]" (str dep) (pr-str version))))))
     (string/join "\n\n")
     (println))))
