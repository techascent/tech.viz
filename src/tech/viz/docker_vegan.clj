(ns tech.viz.docker-vegan
  (:require [tech.io :as io]
            [tech.resource :as resource]
            [tech.io.temp-file :as temp-file]
            [clojure.data.json :as json]
            [clojure.java.shell :as sh])
  (:import [java.util UUID]
           [java.nio.file Paths]
           [java.io File]
           [java.io ByteArrayOutputStream]))


(defn detect-file-type-from-file
  [^String fname]
  (let [extension (.substring fname (inc (.indexOf fname ".")))]
    (keyword extension)))


(defn combine-paths
  [arg & args]
  (-> (Paths/get arg (into-array String args))
      (.toString)))

(defn vegan-temp-fname
  []
  (combine-paths (System/getProperty "java.io.tmpdir")
                 "docker-vegan"))


(defn render-plot->file
  ([vega-data user-output-fname]
   (render-plot->file vega-data user-output-fname {}))
  ([vega-data user-output-fname
    {:keys [temp-dirname]
     :or {temp-dirname (System/getProperty "java.io.tmpdir")}}]
   (resource/stack-resource-context
    (let [vegan-exec-fname (vegan-temp-fname)
          _ (when-not (.exists (io/file vegan-exec-fname))
              (io/interlocked-copy-to-file
               (clojure.java.io/resource "docker-vegan") vegan-exec-fname)
              (.setExecutable (File. vegan-exec-fname) true))
          file-type (detect-file-type-from-file user-output-fname)
          job-id (UUID/randomUUID)
          input-fname (format "%s.json" job-id)
          output-fname (format "%s.%s" job-id (name file-type))
          input-file (combine-paths temp-dirname input-fname)
          output-temp-file (combine-paths temp-dirname output-fname)
          _ (temp-file/watch-file-for-delete input-file)
          _ (temp-file/watch-file-for-delete output-temp-file)
          _ (spit (io/file input-file) (json/write-str vega-data))
          sh-result (sh/sh vegan-exec-fname temp-dirname "-r"
                           (format "/data/%s" input-fname)
                           (format "/data/%s" output-fname))]
      (if (== 0 (:exit sh-result))
        (do
          (io/interlocked-copy-to-file output-temp-file user-output-fname)
          output-fname)
        (throw (Exception. (format "Failed to write vega\n%s"
                                   (:err sh-result)))))))))


(defn render-plot
  ([vega-data
    {:keys [file-type temp-dirname]
     :or {file-type :png
          temp-dirname (System/getProperty "java.io.tmpdir")}
     :as options}]
   (resource/stack-resource-context
    (let [job-id (UUID/randomUUID)
          output-fname (format "%s.%s" job-id (name file-type))
          output-temp-file (combine-paths temp-dirname output-fname)]
      (temp-file/watch-file-for-delete output-temp-file)
      (render-plot->file vega-data output-temp-file options)
      (case file-type
        :png (io/get-image output-temp-file)
        :jpg (io/get-image output-temp-file)
        :jpeg (io/get-image output-temp-file)
        :svg (slurp (io/file output-temp-file))
        :pdf (let [os (ByteArrayOutputStream.)]
               (io/copy output-temp-file os)
               (.toByteArray os))))))
  ([vega-data]
   (render-plot vega-data {})))
