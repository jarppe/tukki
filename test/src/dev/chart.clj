(ns chart
  (:refer-clojure :exclude [apply])
  (:require [clojure.java.io :as io])
  (:import (java.util HexFormat)
           (java.awt Color
                     Stroke
                     BasicStroke
                     Shape
                     Graphics2D
                     Font)
           (java.awt.geom Path2D$Double)
           (java.awt.image BufferedImage)
           (javax.imageio ImageIO)))


(set! *warn-on-reflection* true)


;;
;; Graphics utils:
;;


(defn parse-color ^Color [css-color]
  (let [[r g b a] (map (fn [i]
                         (-> (subs css-color i (+ i 2))
                             (HexFormat/fromHexDigits)))
                       (range 0 (count css-color) 2))]
    (if a
      (Color. (int r) (int g) (int b) (int a))
      (Color. (int r) (int g) (int b)))))


(defn parse-stroke ^Stroke [width]
  (BasicStroke. width BasicStroke/CAP_ROUND BasicStroke/JOIN_ROUND))


(defn draw-text [^Graphics2D g x y ^String text valign]
  (let [font (.getFont g)
        fm   (.getFontMetrics g font)
        b    (.getStringBounds fm text g)
        w    (.getWidth b)
        h    (.getHeight b)
        x    (case valign
               :left x
               :right (- x w)
               :center (+ x (/ w 2.0)))
        y    (+ y (/ h 2.0))]
    (.drawString g text (float x) (float y))))


(defn ->shape ^Shape [[[x y] & data] close?]
  (let [path (Path2D$Double. Path2D$Double/WIND_NON_ZERO
                             (+ (count data) 3))]
    (.moveTo path x y)
    (doseq [[x y] data]
      (.lineTo path x y))
    (when close?
      (.closePath path))
    path))


;;
;; Chart prootocol:
;;


(defprotocol IChart
  (apply [this f])
  (color [this css-color])
  (stroke [this stroke-width])
  (rect [this x y w h])
  (line [this x1 y1 x2 y2])
  (lines [this lines])
  (draw [this points])
  (fill [this points])
  (text [this x y text valign])
  (scale [this x y])
  (translate [this x y])
  (write [this image-file]))


;;
;; Chart implementation:
;;


(defrecord Chart [image-info ^BufferedImage buffered-image ^Graphics2D g]
  IChart

  (apply [this f]
    (let [saved-at (.getTransform g)]
      (f g image-info)
      (.setTransform g saved-at))
    this)

  (color [this css-color]
    (.setColor g (parse-color css-color))
    this)

  (stroke [this stroke-width]
    (.setStroke g (parse-stroke stroke-width))
    this)

  (rect [this x y w h]
    (.fillRect g x y w h)
    this)

  (line [this x1 y1 x2 y2]
    (.drawLine g x1 y1 x2 y2)
    this)

  (lines [this lines]
    (loop [[[x1 y1 x2 y2] & lines] lines]
      (when x1
        (.drawLine g x1 y1 x2 y2)
        (recur lines)))
    this)

  (draw [this points]
    (.draw g (->shape points false))
    this)

  (fill [this points]
    (.fill g (->shape points true))
    this)

  (text [this x y text valign]
    (draw-text g x y text valign)
    this)

  (translate [this x y]
    (.translate g (double x) (double y))
    this)

  (scale [this x y]
    (.scale g (double x) (double y))
    this)

  (write [this image-file]
    (ImageIO/write buffered-image "png" (io/file image-file))
    this)

  java.io.Closeable

  (close [_]
    (.dispose g)))


;;
;; Create chart:
;;


(defn chart ^Chart [image-info]
  (let [buffered-image (BufferedImage. (-> image-info :image-width (int))
                                       (-> image-info :image-height (int))
                                       BufferedImage/TYPE_INT_RGB)]
    (->Chart image-info
             buffered-image
             (doto (.createGraphics buffered-image)
               (.setFont (Font. Font/SANS_SERIF Font/PLAIN 12))))))
