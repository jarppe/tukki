(ns test.scenario.logback
  (:require [clojure.tools.logging :as log])
  (:import (org.slf4j MDC)))


(set! *warn-on-reflection* true)


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn init [])


(defn boz []
  (with-open [_ (MDC/putCloseable "boz" "boz")]
    (log/info "boz 1")))


(defn bar []
  (with-open [_ (MDC/putCloseable "bar" "bar")]
    (log/info "bar 1")
    (boz)
    (log/info "bar 2")))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn test-call []
  (with-open [_ (MDC/putCloseable "foo" "foo")]
    (log/info "foo 1")
    (bar)
    (log/info "foo 2")))

(comment
  (init)
  (test-call)
  (macroexpand-1 (list `log/log clojure.tools.logging/*logger-factory* *ns* :info nil '(print-str "foo")))
  ;
  clojure.tools.logging.impl/get-logger
  clojure.tools.logging/log*)