(ns tukki.log.slf4j.log-service-provider
  (:import (org.slf4j.helpers BasicMDCAdapter
                              BasicMarkerFactory)
           (org.slf4j.bridge SLF4JBridgeHandler))
  (:gen-class :name tukki.log.slf4j.LogServiceProvider
              :implements [org.slf4j.spi.SLF4JServiceProvider]))


;;
;; This ns implements the SLF4J service provider.
;;
;; The SLF4J API initialization looks for resource `META-INF/services/org.slf4j.spi.SLF4JServiceProvider` 
;; and if found it reads the class name from that resource. This library includes this resource with the 
;; class name `tukki.log.slf4j.LogServiceProvider`. This ns is AOT compiled using that class name.
;;
;; SLF4J initializes this class, calls it's `initialize` method, and then the `getLoggerFactory` method.
;;
;; In addition to implementing the SLF4J interfaces this implementation also initializes bridges from:
;;
;;   * clojure.tools.loggings
;;   * JBoss logging
;;   * log4j
;;   * java.util.logging
;;   * Jakarta Commons Logging
;;


(set! *warn-on-reflection* true)


(defn -initialize [_this]
  ;; Initialize tukki:
  (require 'tukki.log.impl)
  ;; Configure clojure.tools.logging to use SLF4J, which in turn is configured to use this library:
  (System/setProperty "clojure.tools.logging.factory"
                      "clojure.tools.logging.impl/slf4j-factory")
  ;; Configure JBoss logging to use SLF4J:
  (System/setProperty "org.jboss.logging.provider"
                      "slf4j")
  ;; Bridge log4j and JUL to SLF4J API:
  (SLF4JBridgeHandler/removeHandlersForRootLogger)
  (SLF4JBridgeHandler/install))


(defn -getLoggerFactory [_this]
  ;; This function is called only once when the SLF4J is first initialized.
  ;;
  ;; The tukki.log.slf4j.logger-factory/make-logger-factory creates an implementation of 
  ;; the SLF4J API ILoggerFactory instance.
  ;;
  ;; Resolve make-logger-factory dynamically so that when this is AOT compiled it does 
  ;; not end up compiling everything.
  (let [make-context  @(requiring-resolve 'tukki.log.slf4j.logger-factory/make-logger-factory)]
    (make-context)))


(defn -getMDCAdapter [_this] (BasicMDCAdapter.))
(defn -getMarkerFactory [_this] (BasicMarkerFactory.))


; Magic value from https://logback.qos.ch/xref/ch/qos/logback/classic/spi/LogbackServiceProvider.html#L27
(defn -getRequestedApiVersion [_this] "2.0.99")
