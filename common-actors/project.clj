(defproject common-actors "0.1.5"
  :description "The common actor between calculation and creation"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :aot [common-actors.core]
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure.gaverhae/okku "0.1.5"]])
