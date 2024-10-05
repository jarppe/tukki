(ns test.scenario.tukki
  (:require [tukki.log :as log]
            [tukki.log.config])
  (:import (java.time.format DateTimeFormatter)))


(set! *warn-on-reflection* true)


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn init []
  (tukki.log.config/vary-config! merge {:simple/color      false
                                        :simple/ts         (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss.SSS")
                                        :simple/mdc        true
                                        :simple/trim-ex-ns false}))


(defn boz []
  (log/with-mdc [boz "boz"]
    (log/info "boz 1")))


(defn bar []
  (log/with-mdc [bar "bar"]
    (log/info "bar 1")
    (boz)
    (log/info "bar 2")))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn test-call []
  (log/with-mdc [foo "foo"]
    (log/info "foo 1")
    (bar)
    (log/info "foo 2")))


(comment
  (init)
  (test-call)
  ;
  )