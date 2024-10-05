(ns tukki.log.appender.json
  (:require [jsonista.core :as json]
            [tukki.log.mdc :as mdc])
  (:import (java.time ZoneOffset)
           (java.time.format DateTimeFormatter)))


(defn stacktrace [^Throwable ex]
  (when ex
    (loop [ex ex
           st []]
      (if ex
        (recur (.getCause ex)
               (conj st (let [ex ^Throwable ex]
                          {:class   (-> ex (.getClass) (.getName))
                           :message (-> ex (.getMessage))
                           :data    (-> ex (ex-data))
                           :trace   (->> (.getStackTrace ex)
                                         (map (fn [^StackTraceElement ste]
                                                {:file   (-> ste (.getFileName))
                                                 :line   (-> ste (.getLineNumber))
                                                 :class  (-> ste (.getClassName))
                                                 :method (-> ste (.getMethodName))})))})))
        st))))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn append-event [^Appendable out ^java.time.Instant inst ns line level args ex]
  (let [ts (->> (.atZone inst ZoneOffset/UTC)
                (.format DateTimeFormatter/ISO_DATE_TIME))]
    (json/write-value out {:ts      ts
                           :level   level
                           :ns      ns
                           :line    line
                           :message args
                           :mdc     mdc/*mdc*
                           :ex      (stacktrace ex)})))
