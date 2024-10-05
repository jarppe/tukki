(ns tukki.log.slf4j.logger-factory
  (:require [tukki.log.slf4j.logger :as logger]))


(set! *warn-on-reflection* true)


(defrecord LoggerFactory [^java.util.Map loggers]
  org.slf4j.ILoggerFactory
  (getLogger [_ logger-name]
    (locking loggers
      (.computeIfAbsent loggers logger-name logger/make-logger))))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn make-logger-factory ^org.slf4j.ILoggerFactory []
  (let [loggers (java.util.HashMap.)]
    (->LoggerFactory loggers)))
