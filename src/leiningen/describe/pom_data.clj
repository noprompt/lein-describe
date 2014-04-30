(ns leiningen.describe.pom-data
  (:require [cemerick.pomegranate.aether :as aether]
            [clojure.java.io :as io]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.string :as string])
  (:import (java.util.jar JarFile)))


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

(defn normalize-xml
  "Return a map of {xml-tag-name content}."
  [xml-data]
  (reduce merge (normalize-xml* xml-data)))

(defn locate-jar
  "Given a dependency name and version attempt to locate it's jar."
  [dep version]
  (let [[group artifact] (group-and-artifact dep)
        group-path (apply io/file (string/split (str group) #"\."))
        ;; TODO: artifact-classifier-version cases won't get handled here.
        jar-basename (format "%s-%s.jar" (str artifact) version)
        file (io/file default-local-repo group-path artifact version jar-basename)]
    (when (.exists file)
      file)))

(defn get-data
  "Given a dependency name and a File attempt to extract data from a
   pom.xml file. Returns the result of xml/parse from the file."
  [dep file]
  (let [[group artifact] (group-and-artifact dep)
        pom-path (format "META-INF/maven/%s/%s/pom.xml" group artifact)
        jar (JarFile. file)
        pom (.getEntry jar pom-path)]
    (and pom (xml/parse (.getInputStream jar pom)))))
