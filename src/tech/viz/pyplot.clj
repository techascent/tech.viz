(ns tech.viz.pyplot
  (:require [tech.viz.vega :as vega]
            [tech.viz.gradients :refer [gradients]]
            [clojure.java.shell :as sh]
            [applied-science.darkstar :as darkstar]
            [clojure.data.json :as json])
  (:import [java.io File]
           [java.nio.file Paths]
           [java.util UUID]))

(defn- path
  ^String [src-path & paths]
  (-> (Paths/get (str src-path) (into-array String (map str paths)))
      (.toString)))

(defn figure
  ([options]
   (let [figsize (:figsize options)
         dpi (:dpi options 96)
         [w h] figsize]
     (merge
      {:$schema "https://vega.github.io/schema/vega-lite/v5.1.0.json"}
      (when figsize
        {:width (* (long w) dpi)
         :height (* (long h) dpi)})
      (select-keys options [:title]))))
  ([] (figure nil)))



(defn- get-color
  [idx options]
  (let [scheme-name (get options :scheme :alpine-colors)
        color (if (keyword? scheme-name)
                 (nth (take-nth 7 (cycle (get gradients scheme-name))) idx)
                 scheme-name)]
    (cond
      (sequential? color)
      (let [[r g b] color]
        (format "rgb(%d, %d, %d)" r g b))
      :else
      color)))


(defn generic-plot
  [fig data options]
  (let [color (:color options (get-color (count (:layer fig)) options))
        dkeys (:data-keys options (set (keys (first data))))]
     (update fig :layer conj
             {:mark {:type (:mark-type options)
                     :color color}
              :data {:values data}
              :encoding
              (->> dkeys
                   (map (fn [k]
                          [k {:field k :type :quantitative :axis {:grid false}}]))
                   (into {}))})))


;;TODO - set y axis


(defn plot
  ([fig x y options]
   (generic-plot fig
                 (mapv #(hash-map :x %1 :y %2) x y)
                 (merge {:mark-type :line} options)))
  ([fig x y]
   (plot fig x y nil)))


(defn scatter
  ([fig x y options]
   (generic-plot fig
                 (mapv #(hash-map :x %1 :y %2) x y)
                 (merge {:mark-type :point} options)))
  ([fig x y] (scatter fig x y nil)))


(defn axvline
  ([fig x options]
   (generic-plot fig [{:x x}] (merge {:mark-type :rule} options)))
  ([fig x]
   (axvline fig x nil)))


(defn axhline
  ([fig y options]
   (generic-plot fig [{:y y}] (merge {:mark-type :rule} options)))
  ([fig y]
   (axhline fig y nil)))


(defn ->json
  [fig]
  (json/write-str fig :escape-slash false))


(defn show
  [fig]
  (let [fname (path (System/getProperty "java.io.tmpdir")
                    (str (UUID/randomUUID) ".svg"))
        svg-data (-> (->json fig)
                     (darkstar/vega-lite-spec->svg))]
    (spit fname svg-data)
    (.deleteOnExit (File. fname))
    (sh/sh "xdg-open" fname)))


(defn ->clipboard
  [fig]
  (let [clip-fn (requiring-resolve 'tech.viz.desktop/->clipboard)]
    (clip-fn (->json fig))))


(defn subplots
  ([options plots]
   (let [n-plots (count plots)
         nrows (long (:nrows options n-plots))
         ncols (long (:ncols options 1))]
     (assert (= n-plots (* nrows ncols)))
     (merge (figure options)
            {:columns ncols
             :concat (mapv #(select-keys % [:layer :title]) plots)})))
  ([plots]
   (subplots nil plots)))


(comment
  (do
    (require '[tech.viz.desktop :refer [->clipboard]])
    (require '[clojure.data.json :as json])
    (defn clip [plt]
      (-> (with-out-str
            (json/pprint-json plt :escape-slash false))
          (->clipboard))))
  )
