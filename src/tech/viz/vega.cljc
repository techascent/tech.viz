(ns tech.viz.vega
  "Vega vizualizations of datasets."
  #?(:clj (:require [clojure.data.json :as json]
                    [applied-science.darkstar :as darkstar]))
  (:require [tech.viz.gradients :refer [gradients]]))


(def gradient-levels 64)

(def default-gradient-set
  #{:gray-yellow-tones
    :alpine-colors
    :blue-green-yellow
    :brown-cyan-tones
    :cherry-tones
    :dark-rainbow
    :rainbow
    :green-red
    :rose-colors
    :temperature-map})


(defn throw-error
  [& args]
  #?(:clj (throw (Exception. (apply str args)))))


(defn gradient-vectors
  [gradient-name input-data]
  (let [data-min (apply min input-data)
        data-max (apply max input-data)
        range (- (long data-max)
                 (long data-min))
        multiplier (if (= 1 (count input-data))
                     1.0
                     (double (/ (dec (long gradient-levels)) range)))
        gradient-vec (cond
                       (keyword? gradient-name)
                       (get gradients gradient-name)
                       (fn? gradient-name)
                       gradient-name
                       :else
                       (throw-error "Unrecognized gradient name: " gradient-name))
        _ (when-not gradient-vec
            (throw-error "Gradient not found" gradient-name))
        data-min (double data-min)]
    (->> input-data
         (mapv (fn [input-data-item]
                 (let [vec-idx
                       (long
                        (* multiplier (- (double input-data-item) data-min)))]
                   (gradient-vec vec-idx)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions for generating vega JS specs for visualization
(defn base-schema
  [options & {:as m}]
  (merge {:$schema "https://vega.github.io/schema/vega/v5.json"
          :autosize {:type "fit" :resize true :contains "padding"}
          :width 800 :height 450}
         options m))

(defn axis
  [& {:keys [domain grid]
      :or {domain false grid true}
      :as m}]
  (merge m {:domain domain :grid grid}))

(defn scale
  [& {:keys [nice round type zero]
      :or {nice true round true type "linear" zero true}
      :as m}]
  (merge m {:nice nice :round round :type type :zero zero}))

(defn default-legends
  [vega-spec {:keys [label-key] :as options}]
  (merge vega-spec
         (when label-key
           {:legends [{:type :symbol
                       :fill "color"
                       :orient (:legend-orient options "right")}]})))


(defn bgr->hex-string
  [[b g r]]
  (format "#%02X%02X%02X" r g b))


(defn- label-key->gradient-map
  [label-key gradient-name mapseq-ds]
  (when label-key
    (cond
      (keyword? gradient-name)
      (->> (map #(get % label-key) mapseq-ds)
           (set)
           (#(gradient-vectors gradient-name
                               (range (count %))))
           (mapv bgr->hex-string))
      (string? gradient-name)
      gradient-name
      :else
      "tableau10")))


(defn scatterplot
  "Render a scatterplot to a vega datastructure.  Dataset is a sequence of maps.
  Optional arguments
  :label-key - Use this ordinal value enable different colors for different points.
  :gradient-name - Name of gradient/color scheme to use for different colors.  Can
    be one of the default gradient keywords or can be a string in which case it is
    passed directly to vega.  For more information about which named color schemes
    are available please see:
    https://vega.github.io/vega/docs/schemes/"
  [mapseq-ds x-key y-key & [options]]
  (let [label-key (:label-key options)
        gradient-map (label-key->gradient-map
                      label-key (:gradient-name options)
                      mapseq-ds)]
    (-> (base-schema
         options
         :axes [(axis :orient "bottom"
                      :scale "x"
                      :title x-key)
                (axis :orient "left"
                      :scale "y"
                      :title y-key)]
         :data [{:name "source"
                 :values (mapv #(select-keys % (if label-key
                                                 [x-key y-key label-key]
                                                 [x-key y-key]))
                               mapseq-ds)}]
         :marks [{:encode {:update {:fill (if label-key
                                            {:scale "color" :field label-key}
                                            {:value "#222"})
                                    :stroke {:value "#222"}
                                    :opacity {:value 0.5}
                                    :shape {:value "circle"}
                                    :x {:field x-key :scale "x"}
                                    :y {:field y-key :scale "y"}}}
                  :from {:data "source"}
                  :type "symbol"}]
         :scales (concat [(scale :domain {:data "source" :field x-key}
                                 :name "x"
                                 :range "width"
                                 :zero false)
                          (scale :domain {:data "source" :field y-key}
                                 :name "y"
                                 :range "height"
                                 :zero false)]
                         (when label-key
                           [{:name "color"
                             :type "ordinal"
                             :domain {:data "source" :field label-key}
                             :range {:scheme gradient-map}}])))
        (default-legends options))))


(defn histogram
  "Render a histograph to a vega datastructure"
  [values label & [{:keys [bin-count gradient-name] :as options
                    :or {gradient-name :gray-yellow-tones}}]]
  (let [n-values (count values)
        [minimum maximum] ((juxt #(apply min %)
                                 #(apply max %)) values)
        bin-count (int (or bin-count
                           (Math/ceil (Math/log n-values))))
        bin-width (double (/ (- maximum minimum) bin-count))
        initial-values (->> (for [i (range bin-count)]
                              {:count 0
                               :left (+ minimum (* i bin-width))
                               :right (+ minimum (* (inc i) bin-width))})
                            (vec))
        values (->> values
                    (reduce (fn [eax v]
                              (let [bin-index (min (int (quot (- v minimum)
                                                              bin-width))
                                                   (dec bin-count))]
                                (update-in eax [bin-index :count] inc)))
                            initial-values))
        color-tensors (->> (map :count values)
                           (vec)
                           (gradient-vectors gradient-name))
        colors (->> color-tensors
                    (map bgr->hex-string))
        values (map (fn [v c] (assoc v :color c)) values colors)]
    (base-schema options
     :axes [{:orient "bottom" :scale "xscale" :tickCount 5 :title label}
            {:orient "left" :scale "yscale" :tickCount 5}]
     :data [{:name "binned"
             :values values}]
     :marks [{:encode {:update
                       {:fill {:field :color}
                        :stroke {:value "#222"}
                        :x {:field :left :scale "xscale" :offset {:value 0.5}}
                        :x2 {:field :right :scale "xscale" :offset {:value 0.5}}
                        :y {:field :count :scale "yscale" :offset {:value 0.5}}
                        :y2 {:value 0 :scale "yscale" :offset {:value 0.5}}}}
              :from {:data "binned"}
              :type "rect"}]
     :scales [(scale :domain [minimum maximum]
                     :range "width"
                     :name "xscale"
                     :zero false
                     :nice false)
              (scale :domain {:data "binned" :field "count"}
                     :range "height"
                     :name "yscale")])))


(defn- minmax
  [data]
  [(apply min data)
   (apply max data)])


(defn time-series
  "Render a time series to a vega datastructure"
  [mapseq-ds x-key y-key & [options]]
  (let [label-key (:label-key options)
        gradient-map (label-key->gradient-map
                      label-key (:gradient-name options)
                      mapseq-ds)
        mapseq-data (if label-key
                      (group-by #(get % label-key) mapseq-ds)
                      {"table" mapseq-ds})
        x-domain (minmax (map #(get % x-key) mapseq-ds))
        y-domain (minmax (map #(get % y-key) mapseq-ds))]
    (-> (base-schema
         options
         :axes [{:orient "bottom" :scale "x" :title x-key}
                {:orient "left" :scale "y" :title y-key}]
         :data (mapv (fn [[k v]]
                       {:name k
                        :values (mapv #(select-keys % (if label-key
                                                        [x-key y-key label-key]
                                                        [x-key y-key]))
                                      v)})
                     mapseq-data)
         :marks (mapv (fn [[k _v]]
                        {:encode
                         {:enter {:stroke (if label-key
                                            {:scale "color" :field label-key}
                                            {:value "#222"})
                                  :strokeWidth {:value 2}
                                  :x {:field x-key :scale "x"}
                                  :y {:field y-key :scale "y"}}}
                         :from {:data k}
                         :type "line"})
                      mapseq-data)
         :scales (concat [{:domain x-domain
                           :name "x"
                           :range "width"
                           :type "utc"}
                          (scale :domain y-domain
                                 :name "y"
                                 :range "height")]
                         (when label-key
                           [{:name "color"
                             :type "ordinal"
                             :domain (vec (set (map #(get % label-key)
                                                    mapseq-ds)))
                             :range {:scheme gradient-map}}])))
        (default-legends options))))

(defn stacked-bar-chart
  "data is a sequence of maps with keys :x :y and :c, sorted by :x
  :x is the groups on the x-axis
  :y is the height of the bar-part for that x
  :c is in (0, 1, ...) and corresponds to both the indexes in the color vector and the bottom-up order of the stack categories"
  [data colors & [options]]
  (base-schema
   options
   :data [{:name "table"
           :values data
           :transform [{:type "stack"
                        :groupby ["x"]
                        :sort {:field "c"}
                        :field "y"}]}]
   :scales [{:name "x"
             :type "band"
             :range "width"
             :domain {:data "table"
                      :field "x"}}
            {:name "y"
             :type "linear"
             :range "height"
             :nice true
             :zero true
             :domain {:data "table"
                      :field "y1"}}
            {:name "color"
             :type "ordinal"
             :range colors
             :domain {:data "table"
                      :field "c"}}]
   :axes [{:orient "bottom" :scale "x" :zindex 1
           :labelAngle -30
           :labelAlign :right}
          {:orient "left" :scale "y" :zindex 1}]
   :marks [{:type "rect"
            :from {:data "table"}
            :encode {:enter {:x {:scale "x" :field "x"}
                             :width {:scale "x" :band 1 :offset -1}
                             :y {:scale "y" :field "y0"}
                             :y2 {:scale "y" :field "y1" :offset 1}
                             :fill {:scale "color" :field "c"}}}}]))

#?(:clj
   (do
     (defn scatterplot->str
       [mapseq-ds x-key y-key & [options]]
       (->> (scatterplot mapseq-ds x-key y-key options)
            (json/write-str)))

     (defn histogram->str
       ([ds col & [options]]
        (-> (histogram ds col options)
            (json/write-str))))

     (defn time-series->str
       [mapseq-ds x-key y-key & [options]]
       (->> (time-series mapseq-ds x-key y-key options)
            (json/write-str)))

     (defn vega->svg
       [vega-spec]
       (darkstar/vega-spec->svg (json/write-str vega-spec)))


     (defn vega->svg-file
       [vega-spec filename]
       (spit filename (vega->svg vega-spec))))

   )


(comment

  (do
    (require '[tech.v2.datatype :as dtype])
    (require '[tech.v2.datatype.datetime.operations :as dtype-dt-ops])
    (require '[tech.v2.tensor.color-gradients :as gradient])
    (require '[tech.v2.tensor :as dtt])
    (require '[tech.ml.dataset :as ds])
    (require '[clojure.java.shell :as sh])
    (defn expand-gradient
      [gradient-name]
      (mapv vec
            (-> (dtt/reshape (range gradient-levels)
                             [1 gradient-levels])
                (gradient/colorize gradient-name)
                (dtt/reshape [gradient-levels 3])
                (dtt/slice 1))))

    (defn write-gradients
      []
      (spit "src/tech/viz/gradients.cljc"
            (pr-str
             (->> default-gradient-set
                  (map (fn [k]
                         [k (expand-gradient k)]))
                  (into {})))))
    (def desktop-default-options {:background "#FFFFFF"})


    (defn desktop-view-vega
      [vega-spec filename]
      (vega->svg-file vega-spec filename)
      (sh/sh "xdg-open" filename))


     )


  (def example-scatterplot
    (-> (ds/->dataset "test/data/spiral-ds.csv")
        (ds/mapseq-reader)
        (scatterplot "x" "y"
                     (merge {:title "Spriral Dataset"
                             :label-key "label"}
                            desktop-default-options))
        (vega->svg-file "scatterplot.svg")))

  ;;Now open https://vega.github.io/editor/ and paste.

  (def example-histogram
    (-> (slurp "https://vega.github.io/vega/data/cars.json")
        (clojure.data.json/read-str :key-fn keyword)
        (ds/->dataset)
        (ds/column :Displacement)
        (histogram "Displacement" (merge desktop-default-options
                                         {:bin-count 15
                                          :title "Displacement Histogram"}))))


  (desktop-view-vega example-histogram "histogram.svg")


  (def timeseries-data
    (as-> (ds/->dataset "https://vega.github.io/vega/data/stocks.csv") ds
      ;;The time series chart expects time in epoch milliseconds
      (ds/add-or-update-column ds "date"
                               (dtype-dt-ops/get-epoch-milliseconds (ds "date")))
      (ds/mapseq-reader ds)
      (time-series ds "date" "price" (merge
                                      desktop-default-options
                                      {:title "Stock Price"
                                       :label-key "symbol"}))
      (vega->svg-file ds "timeseries.svg")))

  (def timeseries-subset
    (as-> (ds/->dataset "https://vega.github.io/vega/data/stocks.csv") ds
      ;;The time series chart expects time in epoch milliseconds
      (ds/add-or-update-column ds "year" (dtype-dt-ops/get-years (ds "date")))
      (ds/filter-column #{2007 2008 2009} "year" ds)
      (ds/update-column ds "date" dtype-dt-ops/get-epoch-milliseconds)
      (ds/mapseq-reader ds)
      (time-series ds "date" "price" (merge
                                      desktop-default-options
                                      {:title "Stock Price (2007-2010)"
                                       :label-key "symbol"}))
      (vega->svg-file ds "timeseries.svg")))


  (desktop-view-vega example-timeseries "timeseries.svg")


  (let [ds (-> (ds/->dataset "https://vega.github.io/vega/data/seattle-temps.csv")
               (ds/select :all (range 1000)))
        sdf (java.text.SimpleDateFormat. "yyyy/MM/dd HH:mm")
        utc-ms (map #(.getTime (.parse sdf %)) (ds "date"))]
    (-> (ds/new-column ds "inst" utc-ms {:datatype :int64})
        (ds/->flyweight)
        (time-series->str "inst" "temp")
        (->clipboard)))

  ;; Then paste into: https://vega.github.io/editor

  )
