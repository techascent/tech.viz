(defproject techascent/tech.viz "0.1-SNAPSHOT"
  :description "Visualization library"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [techascent/tech.datatype "5.0-alpha-3"]
                 [techascent/tech.io "3.16"]]
  :profiles {:dev {:dependencies [[techascent/tech.ml.dataset "2.0-beta-5"]]}})
