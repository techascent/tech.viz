(defproject techascent/tech.viz "0.1-SNAPSHOT"
  :description "A Clojure library for visualizing data."
  :url "https://github.com/techascent/tech.viz"
  :license {:name "Coypright 2020, TechAscent"
            :url "http://techasecent.com"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [techascent/tech.datatype "4.72"]]
  :plugins [[s3-wagon-private "1.3.1"]]
  :profiles {:dev {:dependencies [[techascent/tech.ml.dataset "1.68"]]}}
  :repl-options {:init-ns tech.viz.vega}
  :repositories {"snapshots" {:url "s3p://techascent.jars/snapshots/"
                              :no-auth true
                              :releases false}
                 "releases"  {:url "s3p://techascent.jars/releases/"
                              :no-auth true
                              :snapshots false
                              :sign-releases false}}
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "" "--no-sign"] ;; disable signing
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])
