(defproject techascent/tech.viz "0.4-SNAPSHOT"
  :description "Simple Vega visualization library"
  :url "http://github.com/techascent/tech.viz"
  :license {:name "EPL-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
    :plugins [[lein-tools-deps "0.4.5"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]}
  :profiles {:dev {:lein-tools-deps/config {:resolve-aliases [:test]}}
             :uberjar {:aot :all}})
