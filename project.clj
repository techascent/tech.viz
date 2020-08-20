(defproject techascent/tech.viz "0.4.3"
  :description "Simple Vega visualization library"
  :url "http://github.com/techascent/tech.viz"
  :license {:name "EPL-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.json "0.2.7"]
                 [metasoarous/darkstar "0.1.0"]]
  :profiles {:uberjar {:aot :all}
             :test {:dependencies [[techascent/tech.ml.dataset "4.02"]
                                   [ch.qos.logback/logback-classic "1.1.3"]
                                   [http-kit "2.4.0"]]}}
  :repositories [["releases" {:url "https://repo.clojars.org"
                              :creds :gpg}]])
