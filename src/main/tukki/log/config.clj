(ns tukki.log.config
  (:require [tukki.log.state :as state]))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn vary-config!
  "Apply given function to current logging config and set the return value as new logging config."
  [f & args]
  (apply alter-var-root #'state/state f args)
  ;; Ensure the appender ns is required and symbol is resolvable:
  (requiring-resolve (:appender state/state))
  nil)


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn set-config!
  "Set the given config as new logging config."
  [config]
  (vary-config! (constantly config))
  nil)


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn set-root-level!
  "Set the root logging level."
  [level]
  (vary-config! assoc :root-level level))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn set-log-level!
  "Set the logging level for given logger (string, symbol, or namespace) to given level."
  [logger level]
  (let [logger-name (cond
                      (string? logger) logger
                      (symbol? logger) (name logger)
                      (instance? clojure.lang.Namespace logger) (-> logger (ns-name) (name))
                      :else (throw (ex-info "illegal logger type, expected string, symbol or namespace" {:logger logger})))]
    (vary-config! update :levels assoc logger-name level)))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn set-log-appender!
  "Set the logging appender."
  [appender]
  (vary-config! assoc :appender (or (get {:simple 'tukki.log.appender.simple/append-event
                                          :json   'tukki.log.appender.json/append-event}
                                         appender)
                                    (cond
                                      (symbol? appender) appender
                                      (string? appender) (symbol appender)
                                      (var? appender)    (.toSymbol ^clojure.lang.Var appender)
                                      :else (throw (ex-info "illegal value for appender, expected symbol, string or var" {:appender appender}))))))
