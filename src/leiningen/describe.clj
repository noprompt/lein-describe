(ns leiningen.describe
  (:require [leiningen.core.classpath :as classpath]
            [leiningen.core.main :as main]
            [leiningen.core.user :as user]
            [cemerick.pomegranate.aether :as aether]
            [clojure.java.io :as io]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.string :as string]
            [clojure.pprint :refer [pprint pp]])
  (:import (java.util.jar JarFile)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; POM data

(defn- normalize-xml* [xml-data]
  (let [z (-> xml-data zip/xml-zip zip/down)]
    (if (nil? z)
      (:content xml-data)
      (loop [z z]
        (if (zip/end? z)
          (:content (zip/root z))
          (-> z
              (zip/edit (fn [{:keys [tag content] :as node}]
                          (if (string? (first content))
                            {tag content}
                            {tag (normalize-xml* node)})))
              (zip/next)
              (recur)))))))

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
    (and pom (xml/parse (.getInputStream jar pom)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Display helpers

(defn- format-dependency [dep version]
  (if version
    (format "[%s %s]" (str dep) (pr-str version))
    (format "[%s]" (str dep))))

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
                v (dependency-version d)]]
      (format-dependency n v))))

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

(defn- remove-user-profile-dependencies
  "Return an updatede project map with user profile dependencies
  removed."
  [project]
  (let [profile-deps (map
                      (fn [[dep version & more]]
                        ;; Dependency symbols from a project posess
                        ;; both a namespace and a name, the ones from
                        ;; (user/profile) do not...
                        (let [dep (if-not (namespace dep)
                                    (symbol (str dep) (str dep))
                                    dep)]
                          (into [dep version] more)))
                      (get-in (user/profiles) [:user :dependencies]))]
    (update-in project [:dependencies]
               (fn [project-deps]
                 (remove (set profile-deps) project-deps)))))

(defn- get-dependencies-from-key
  "Return a map of {[dependency version & more] #<File /path/to.jar>}."
  [project k]
  (as-> (classpath/get-dependencies k project) _
        (zipmap (keys _) (aether/dependency-files _))
        (select-keys _ (k project))))

(defn- get-project-dependencies [project]
  (-> (remove-user-profile-dependencies project)
      (get-dependencies-from-key :dependencies)))

(defn- get-plugin-dependencies [project]
  (get-dependencies-from-key project :plugins))

(defn- lines-for-dependencies [deps]
  (string/join
   "\n\n"
   (for [[[dep version] file] deps
         :let [data (get-pom-data dep file)]
         ;; Clojure is almost always going to be a dependency of any
         ;; Leiningen project, including it in the output seems
         ;; unecessary.
         :when (not= "clojure" (name dep))]
     (if data
       (try
         (lines (normalize-xml data))
         (catch Exception e
           (str "Error: There was a problem describing " (format-dependency dep version))))
       (str "Could not find data for " (format-dependency dep version))))))

(def ^:private separator
  (apply str (repeat 72 "-")))

(defn- display-project-dependencies [project]
  (let [project-deps (get-project-dependencies project)]
    (println "PROJECT DEPENDENCIES:")
    (println separator)
    (println (lines-for-dependencies project-deps))))

(defn- display-plugin-dependencies [project]
  (let [plugin-deps (get-plugin-dependencies project)]
    (println "PLUGIN DEPENDENCIES:")
    (println separator)
    (println (lines-for-dependencies plugin-deps))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CLI 

(defn describe
  "Display information about project dependecies."
  [project & args]
  (display-project-dependencies project)
  (print "\n\n")
  (display-plugin-dependencies project))
