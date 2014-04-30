(ns leiningen.describe-test
  [:use [clojure.test]
   [leiningen.describe]])

(def sample-project
  {:name "sample-project"
   :dependencies [["org.clojure/clojure" "1.5.1"]
                  ["org.clojure/tools.reader" "0.8.1"]]
   :plugins [["lein-difftest/lein-difftest" "2.0.0"]
             ["com.jakemccrary/lein-test-refresh" "0.2.0"]]
   })

(deftest should-do-something
  (let [text (as-text sample-project)]
    (is (not (empty? text)))
    (is (= true (.contains text "tools.reader")))
    (is (= true (.contains text "lein-difftest")))))
