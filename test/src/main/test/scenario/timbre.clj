(ns test.scenario.timbre
  (:require [clojure.string :as str]
            [taoensso.timbre :as log :refer [with-context+]])
  (:import (java.time Instant
                      ZoneOffset)
           (java.time.format DateTimeFormatter)))


(set! *warn-on-reflection* true)


(def ^DateTimeFormatter formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss.SSS"))


(defn format-instant ^String [^Instant inst]
  (->> (.atZone inst ZoneOffset/UTC)
       (.format formatter)))


(defn output-fn [event]
  (let [date (-> event :instant)
        inst (.toInstant ^java.util.Date date)]
    (-> (doto (StringBuilder. 128)
          (.append (format-instant inst)) (.append " ")
          (.append (-> event :level (name))) (.append " ")
          (.append (-> event :?ns-str (or "?"))) (.append " ")
          (.append (->> event :vargs (str/join " "))) (.append " ")
          (.append (-> event :context (pr-str))))
        (.toString))))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn init []
  (let [level (-> (System/getProperty "logtest.scenario.timbre.level")
                  (keyword))]
    (log/merge-config! {:min-level [[#{"test.*"} level]
                                    [#{"*"} :fatal]]
                        :output-fn output-fn})))


(defn boz []
  (with-context+ {:boz "boz"}
    (log/info "boz 1")))


(defn bar []
  (with-context+ {:bar "bar"}
    (log/info "bar 1")
    (boz)
    (log/info "bar 2")))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn test-call []
  (with-context+ {:foo "foo"}
    (log/info "foo 1")
    (bar)
    (log/info "foo 2")))


(comment
  (init)
  (test-call)
  ;
  )
