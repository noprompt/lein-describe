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
;; Utilities

;; Take from aether. Not sure why this isn't public.
(def ^{:private true} default-local-repo
  (io/file (System/getProperty "user.home") ".m2" "repository"))

(defn- group-and-artifact
  "Return a vector of [group artifact] from a dependency name."
  [dep]
  (let [dep (symbol dep)
        group (or (namespace dep) (name dep))
        artifact (name dep)]
    [group artifact]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; POM data
;; See the approach in clojars for interfacing with maven
;; https://github.com/ato/clojars-web/blob/master/src/clojars/maven.clj
;; [org.apache.maven/maven-model "3.0.4"
;;  :exclusions
;;  [org.codehaus.plexus/plexus-utils]]

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

(defn- locate-jar
  "Given a dependency name and version attempt to locate it's jar."
  [dep version]
  (let [[group artifact] (group-and-artifact dep)
        group-path (apply io/file (string/split (str group) #"\."))
        ;; TODO: artifact-classifier-version cases won't get handled here.
        jar-basename (format "%s-%s.jar" (str artifact) version)
        file (io/file default-local-repo group-path artifact version jar-basename)]
    (when (.exists file)
      file)))

(defn- get-pom-data
  "Given a dependency name and a File attempt to extract data from a
   pom.xml file. Returns the result of xml/parse from the file."
  [dep file]
  (let [[group artifact] (group-and-artifact dep)
        pom-path (format "META-INF/maven/%s/%s/pom.xml" group artifact)
        jar (JarFile. file)
        pom (.getEntry jar pom-path)]
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

(defn dependency-scope [data]
  (or (first (:scope data)) "compile"))

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
    (str "Dependencies: " (if (seq ds)
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

(defn ^:private xml->data [data]
  {:name (dependency-name data)
   :version (dependency-version data)
   :url (dependency-url data)
   :description (dependency-description data)
   :licenses (dependency-licenses data)
   :scope (dependency-scope data)
   :dependencies
   (for [dep (:dependencies data)
         :let [d (reduce merge (:dependency dep))]]
     (xml->data d))})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Dependency helpers

(defn- remove-user-profile-dependencies-from-key [project k]
  (let [profile-deps (map
                      (fn [[dep version & more]]
                        ;; Dependency symbols from a project posess
                        ;; both a namespace and a name, the ones from
                        ;; (user/profile) do not...
                        (let [dep (if-not (namespace dep)
                                    (symbol (str dep) (str dep))
                                    dep)]
                          (into [dep version] more)))
                      (get-in (user/profiles) [:user k]))]
    (update-in project [k]
               (fn [x-deps]
                 (remove (set profile-deps) x-deps)))))

(defn- remove-user-profile-dependencies
  "Return an updated project map with user profile dependencies
  removed."
  [project]
  (remove-user-profile-dependencies-from-key project :dependencies))

(defn- get-dependencies-from-key
  "Return a map of {[dependency version & more] #<File /path/to.jar>}."
  [project k]
  (reduce
   (fn [m [dep version & more]]
     (assoc m [dep version] (locate-jar dep version)))
   {}
   (k project)))

(defn- get-project-dependencies [project]
  (-> (remove-user-profile-dependencies-from-key project :dependencies)
      (get-dependencies-from-key :dependencies)))

(defn- get-plugin-dependencies [project]
  (-> (remove-user-profile-dependencies-from-key project :plugins)
      (get-dependencies-from-key :plugins)))

(defn- dependency-map [deps]
  (for [[[dep version] file] deps
        :let [data (and file (get-pom-data dep file))]]
    (if data
      (try
        (xml->data (normalize-xml data))
        (catch Exception e
          (str "Error: There was a problem describing " (format-dependency dep version))))
      (str "Could not find data for " (format-dependency dep version)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; String Report

(defn- lines-for-dependencies [deps-file-map]
  (let [all-deps-data (dependency-map deps-file-map)]
    (string/join
         "\n\n"
         (for [data all-deps-data]
           (if (map? data)
             (string/join "\n" (data->strings data))
             data)))))

(def ^:private separator
  (apply str (repeat 72 "-")))

(defn- display-project-dependencies [project]
  (let [project-deps (get-project-dependencies project)]
    (println "PROJECT DEPENDENCIES:")
    (println separator)
    (println (if (seq project-deps)
               (lines-for-dependencies project-deps)
               ;; Highly unlikely but for the sake of consistency.
               "This project has no dependencies."))))

(defn- display-plugin-dependencies [project]
  (let [plugin-deps (get-plugin-dependencies project)]
    (println "PLUGIN DEPENDENCIES:")
    (println separator)
    (println (if (seq plugin-deps)
               (lines-for-dependencies plugin-deps)
               "This project has no plugins."))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CLI

(def sample-project
  {:name "sample-project"
   :dependencies [["org.clojure/clojure" "1.5.1"]
                  ["org.clojure/tools.reader" "0.8.1"]]
   :plugins [["lein-difftest/lein-difftest" "2.0.0"]
             ["com.jakemccrary/lein-test-refresh" "0.2.0"]]
   })

(defn describe
  "Display information about project dependencies."
  [project & args]
  (display-project-dependencies project)
  (print "\n\n")
  (display-plugin-dependencies project))
