(ns tech.viz.docker-vegan
  "This requires the docker-vegan script from this scripts directory be
  install on your path."
  (:require [clojure.java.shell :as sh]
            [clojure.data.json :as json]
            [clojure.string :as s]
            [tech.io.temp-file :as temp-file]
            [tech.resource :as resource]
            [tech.io :as io])
  (:import [java.awt.image BufferedImage]
           [java.util UUID]
           [java.nio.file Paths Path]
           [java.io ByteArrayOutputStream]))

(defn- combine-paths
  [& paths]
  (-> (Paths/get (first paths)
                 (into-array String (rest paths)))
      (.toString)))


(defn- detect-file-type-from-file
  [fname]
  (let [fname (.toLowerCase ^String fname)]
    (cond
      (.endsWith fname "png")
      :png
      (or (.endsWith fname "jpeg")
          (.endsWith fname "jpg"))
      :jpg
      (.endsWith fname "pdf")
      :pdf
      (.endsWith fname "svg")
      :svg
      :else
      (throw (Exception. "Urecognized file type: %s" fname)))))


(defn render-plot->file
  ([vega-data user-output-fname]
   (render-plot->file vega-data user-output-fname {}))
  ([vega-data user-output-fname
    {:keys [temp-dirname]
     :or {temp-dirname (System/getProperty "java.io.tmpdir")}}]
   (resource/stack-resource-context
    (let [file-type (detect-file-type-from-file user-output-fname)
          job-id (UUID/randomUUID)
          input-fname (format "%s.json" job-id)
          output-fname (format "%s.%s" job-id (name file-type))
          input-file (combine-paths temp-dirname input-fname)
          output-temp-file (combine-paths temp-dirname output-fname)
          _ (temp-file/watch-file-for-delete input-file)
          _ (temp-file/watch-file-for-delete output-temp-file)
          _ (spit (io/file input-file) (json/write-str vega-data))
          sh-result (sh/sh "docker-vegan" temp-dirname "-r"
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


(defn validate-plot
  ([vega-data
     {:keys [temp-dirname]
      :or {temp-dirname (System/getProperty "java.io.tmpdir")}}]
   (resource/stack-resource-context
    (let [job-id (UUID/randomUUID)
          input-fname (format "%s.json" job-id)
          input-file (combine-paths temp-dirname input-fname)
          _ (temp-file/watch-file-for-delete input-file)
          _ (spit (io/file input-file) (json/write-str vega-data))
          sh-result (sh/sh "docker-vegan" temp-dirname "-v"
                           (format "/data/%s" input-fname))
          validation-result (:err sh-result)
          validation-lines (s/split validation-result #"\n")]
      ;;The first 3 lines of the validation result are crap output printed by ajv
      (s/join "\n" (drop 3 validation-lines)))))
  ([vega-data]
   (validate-plot vega-data {})))
