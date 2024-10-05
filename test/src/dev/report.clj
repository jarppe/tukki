(ns report
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.math :refer [round]]
            [dev.onionpancakes.chassis.core :as h]
            [jfr]
            [chart :as c])
  (:import (java.io File)))


(set! *warn-on-reflection* true)


(java.util.Locale/setDefault java.util.Locale/US)


(def ^File reports (io/file "report"))


(defn load-recordings []
  (->> (.listFiles reports ^java.io.FilenameFilter (fn [_ name] (str/ends-with? name ".jfr")))
       (map File/.getName)
       (reduce (fn [acc recording]
                 (let [[lib level type] (->> (str/split recording #"-")
                                             (map keyword))]
                   (assoc-in acc [type level lib] (jfr/parse-recording (io/file reports recording)))))
               {})))


(defn aggregate-data [agg-width-ms agg-fn events]
  ;; Would transducer be better here?
  (let [point                     (fn [slot value]
                                    [(double (* slot agg-width-ms)) value])
        {:keys [data slot value]} (reduce (fn [acc event]
                                            (let [{:keys [data slot value]} acc
                                                  [ts new-value]            event
                                                  event-slot                (quot ts agg-width-ms)]
                                              (cond
                                                (nil? acc) {:data  []
                                                            :slot  event-slot
                                                            :value new-value}
                                                (= slot event-slot) (update acc :value agg-fn new-value)
                                                :else (assoc acc
                                                             :data (conj data (point slot value))
                                                             :slot event-slot
                                                             :value new-value))))
                                          nil
                                          events)]
    (conj data (point slot value))))


(defn scale-with [scale-x scale-y data]
  (map (fn [[x y]]
         [(scale-x x) (scale-y y)])
       data))


(defn into-bars [data]
  (cons [0.0 0.0]
        (->> (concat [[0.0 0.0]] data)
             (partition-all 2 1)
             (reduce (fn [acc [[px py] [nx ny]]]
                       (if nx
                         (-> acc
                             (conj [nx py])
                             (conj [nx ny]))
                         (conj acc [px 0.0])))
                     []))))


(def background-color "1C1C1Cff")
(def gridline-color "67676750")
(def heap-line-color "488c44a0")
(def heap-fill-color "488c4460")
(def heap-unit-color "488c44a0")
(def gc-pause-line-color "B2606Aa0")
(def gc-pause-fill-color "522F3360")
(def gc-pause-unit-color "B2606Ad0")


(defn generate-heap-images [recordings image-info]
  (let [recordings   (-> recordings :mem :info)
        max-duration (->> recordings
                          (vals)
                          (map (fn [events]
                                 (let [start-time (-> events (first) :ts)
                                       end-time   (-> events (last) :ts)]
                                   (- end-time start-time))))
                          (reduce max))
        max-mem      (->> recordings
                          (vals)
                          (map (fn [events]
                                 (some (fn [event]
                                         (when (-> event :type (= :mem))
                                           (:max-size event)))
                                       events)))
                          (reduce max))
        max-gc-pause (->> recordings
                          (vals)
                          (mapcat (fn [recording]
                                    (->> recording
                                         (filter (fn [event] (-> event :type (= :gc))))
                                         (map (fn [{:keys [ts sum-of-pauses]}] [ts sum-of-pauses]))
                                         (aggregate-data (quot max-duration (quot (:image-width image-info)
                                                                                  (:longest-pause-agg-width image-info)))
                                                         +)
                                         (map second))))
                          (reduce max))
        image-height (-> image-info :image-height)
        image-width  (-> image-info :image-width)]
    (doseq [lib [:logback :timbre :tukki]]
      (let [recording (-> recordings lib)
            start-ts  (-> recording (first) :ts)
            heap      (->> recording
                           (filter (fn [event] (-> event :type (= :heap))))
                           (map (fn [{:keys [ts heap]}] [ts heap]))
                           (aggregate-data (quot max-duration (quot (:image-width image-info)
                                                                    (:heap-agg-width image-info)))
                                           max))
            gc-pause  (->> recording
                           (filter (fn [event] (-> event :type (= :gc))))
                           (map (fn [{:keys [ts sum-of-pauses]}] [ts sum-of-pauses]))
                           (aggregate-data (quot max-duration (quot (:image-width image-info)
                                                                    (:longest-pause-agg-width image-info)))
                                           +))

            scale-x   (let [ts-scale (/ (double image-width) max-duration)]
                        (fn [ts]
                          (* (- ts start-ts) ts-scale)))]
        (with-open [chart (c/chart image-info)]
          (doto chart
            (c/color background-color)
            (c/rect 0 0 image-width image-height)
            (c/color gridline-color)
            (c/lines (for [x (range 0 image-width (quot image-width 10))]
                       [x 0 x image-width]))
            (c/lines (for [y (range 0 image-height (quot image-height 10))]
                       [0 y image-width y])))

          (c/color chart heap-unit-color)
          (c/text chart (/ image-width 2.0) 10 (name lib) :center)

          (c/color chart heap-unit-color)
          (doseq [[y s] (for [y (range 0 (inc image-height) (quot image-height 10))]
                          [y (format "%.1f MB" (-> (- image-height y)
                                                   (double)
                                                   (/ image-height)
                                                   (* max-mem)
                                                   (/ 1024.0)
                                                   (/ 1024.0)))])]
            (c/text chart 10 (-> y (max 20) (min (- image-height 20))) s :left))

          (c/color chart gc-pause-unit-color)
          (doseq [[y s] (for [y (range 0 (inc image-height) (quot image-height 10))]
                          [y (format "%.1f ms" (-> (- image-height y)
                                                   (double)
                                                   (/ image-height)
                                                   (* max-gc-pause)
                                                   (/ 1024.0)
                                                   (/ 1024.0)))])]
            (c/text chart (- image-width 10) (-> y (max 20) (min (- image-height 20))) s :right))

          (c/translate chart 0 image-height)
          (c/scale chart 1.0 -1.0)
          (let [scale-y (fn [value]
                          (* (/ (double value) max-mem)
                             image-height))
                heap    (scale-with scale-x scale-y heap)
                heap    (concat [[0.0 0.0]]
                                heap
                                [[(-> heap (last) (first)) 0.0]])]
            (doto chart
              (c/stroke 2)
              (c/color heap-fill-color)
              #_(c/fill heap)
              (c/color heap-line-color)
              (c/draw heap)))
          (let [scale-y  (fn [value]
                           (* (/ (double value) max-gc-pause)
                              image-height
                              0.5))
                gc-pause (->> gc-pause
                              (scale-with scale-x scale-y)
                              (into-bars))]
            (doto chart
              (c/stroke 2)
              (c/color gc-pause-fill-color)
              (c/fill gc-pause)
              (c/color gc-pause-line-color)
              (c/draw gc-pause))
            (c/write chart (io/file reports (str (name lib) ".png")))))))))


(comment
  (def recordings (load-recordings))
  (generate-heap-images recordings {:image-width             1280
                                    :image-height            900
                                    :heap-agg-width          2
                                    :longest-pause-agg-width (/ 1280 20)}))


(def style "
  *, *::before, *::after {
    box-sizing:  border-box;
    margin:      0;
    padding:     0;
  }
  body { margin-inline: 3rem; font-family: sans-serif; }
  h1 { font-size: 120%; display: flex; gap: 0.5em; align-items: center; margin-top: 3rem; }
  h2 { font-size: 100%; display: flex; gap: 0.5em; align-items: center; margin-block: 1rem; }
  span.info { }
  span.pil { 
    padding: 0.3em 0.6em; 
    border-radius: 1em; 
    display: inline-block;
    margin-left: 1em;
  }
  span.api { background: red; color: white; }
  span.lib { background: green; color: white; }
  span.ctx { background: blue; color: white; }
  .stats { width: 100%; margin-bottom: 1rem; white-space: nowrap; }
  .stats thead tr td { border-bottom: 1px solid black; }
  .stats td:nth-child(1n+4) { border-left: 1px solid black; }
  .stats td:nth-last-child(1) { width: 80%; }
  .stats tbody tr:nth-child(odd) { background-color: #eee; }
  .stats tr { padding-block: 0.2em; }
  .stats td { padding: 0.5em; text-align: center; }
  .stats td.r { text-align: right; }
  .exec { height: 1.2em; background-color: red; }
  ")


(defn generate-html-report [recordings]
  (let [max-duration (->> recordings :speed (vals) (mapcat vals)
                          (map (fn [recording]
                                 (let [start-time (-> recording (first) :ts)
                                       end-time   (-> recording (last) :ts)
                                       duration   (- end-time start-time)]
                                   (/ (double duration) 1000.0))))
                          (reduce max))]
    (->> [h/doctype-html5
          [:html {:lang "en"}
           [:head
            [:meta {:charset "utf-8"}]
            [:title "Logging results"]
            [:meta {:name    "viewport"
                    :content "width=device-width, initial-scale=1.0"}]
            [:style style]]
           [:body
            [:h2 "Speed test:"]
            [:table.stats
             [:thead
              [:tr
               [:td "Library"]
               [:td "Level"]
               [:td "Time (sec)"]
               [:td]]]
             [:tbody
              (for [level [:info :error]
                    lib   [:logback :timbre :tukki]
                    :let  [recording (-> recordings :speed level lib)
                           duration (let [start-time (-> recording (first) :ts)
                                          end-time   (-> recording (last) :ts)
                                          duration   (- end-time start-time)]
                                      (/ (double duration) 1000.0))]]
                [:tr
                 [:td (name lib)]
                 [:td (name level)]
                 [:td.r (format "%.3f" duration)]
                 [:td
                  [:div.exec {:style {:width (-> (/ duration max-duration)
                                                 (* 100.0)
                                                 (str "%"))}}]]])]]
            [:h2 "Memory test:"]
            (for [lib [:logback :timbre :tukki]]
              [:div
               [:h2]
               [:img {:src (str (name lib) ".png")}]])]]]
         (h/html)
         (spit "./report/index.html"))))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn generate-report [_]
  (let [recordings (load-recordings)]
    (generate-heap-images recordings (merge {:image-width             1280
                                             :image-height            900
                                             :heap-agg-width          2
                                             :longest-pause-agg-width (/ 1280 20)}))
    (generate-html-report recordings)))


(comment

  (def recordings (load-recordings))

  (generate-heap-images recordings {:image-width             1280
                                    :image-height            900
                                    :heap-agg-width          2
                                    :longest-pause-agg-width (/ 1280 20)})

  (generate-html-report recordings)
  ;
  )