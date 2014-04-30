(ns leiningen.describe.reports.text
  (:require [leiningen.core.user :as user]
            [leiningen.describe.pom-data :as pom]))

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
  "Return an updated project map with user profile dependencies removed."
  [project]
  (remove-user-profile-dependencies-from-key project :dependencies))

(defn- get-dependencies-from-key
  "Return a map of {[dependency version & more] #<File /path/to.jar>}."
  [project k]
  (reduce
   (fn [m [dep version & more]]
     (assoc m [dep version] (pom/locate-jar dep version)))
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
        :let [data (and file (pom/get-data dep file))]]
    (if data
      (try
        (xml->data (pom/normalize-xml data))
        (catch Exception e
          (str "Error: There was a problem describing " (format-dependency dep version))))
      (str "Could not find data for " (format-dependency dep version)))))
