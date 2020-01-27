(defproject techascent/tech.viz "0.1-SNAPSHOT"
  :description "Visualization library"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [techascent/tech.datatype "4.72"]
                 [techascent/tech.io "3.12"]]
  :profiles {:dev {:dependencies [[techascent/tech.ml.dataset "1.68"]]}}
  :repl-options {:init-ns tech.viz})
