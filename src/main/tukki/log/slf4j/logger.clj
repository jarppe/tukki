(ns tukki.log.slf4j.logger
  (:require [tukki.log.state :as state]
            [tukki.log.impl :as impl]))


(set! *warn-on-reflection* true)


(defn format-message [message args]
  (let [matcher (re-matcher #"\{\}" message)
        builder (StringBuilder.)]
    (loop [start 0
           args  args]
      (if (.find matcher)
        (do (.append builder (subs message start (.start matcher)))
            (.append builder (str (first args)))
            (recur (.end matcher)
                   (rest args)))
        (cons (.toString builder) args)))))


(defn print-log-message [logger-name level format? message args ex]
  (when (impl/logger-enabled-for? logger-name level)
    (impl/print-log-message (-> state/state :appender (eval))
                            logger-name
                            -1
                            level
                            (cond-> (if format?
                                      (format-message message args)
                                      (cons message args))
                              ex (conj ex))))
  nil)


(defrecord Logger [logger-name]

  ;;
  ;; org.slf4j.Logger
  ;; ================
  ;;

  org.slf4j.Logger
  (getName [_] logger-name)

  (isTraceEnabled [_] true)
  (^void trace [_ ^String message] (print-log-message logger-name :trace false message nil nil))
  (^void trace [_ ^String format ^Object arg] (print-log-message logger-name :trace true format [arg] nil))
  (^void trace [_ ^String format ^Object arg1 ^Object arg2] (print-log-message logger-name :trace true format [arg1 arg2] nil))
  (^void trace [_ ^String format ^objects args] (print-log-message logger-name :trace true format args nil))
  (^void trace [_ ^String message ^Throwable e] (print-log-message logger-name :trace false message nil e))

  (isDebugEnabled [_] true)
  (^void debug [_ ^String message] (print-log-message logger-name :debug false message nil nil))
  (^void debug [_ ^String format ^Object arg] (print-log-message logger-name :debug true format [arg] nil))
  (^void debug [_ ^String format ^Object arg1 ^Object arg2] (print-log-message logger-name :debug true format [arg1 arg2] nil))
  (^void debug [_ ^String format ^objects args] (print-log-message logger-name :debug true format args nil))
  (^void debug [_ ^String message ^Throwable e] (print-log-message logger-name :debug false message nil e))

  (isInfoEnabled [_] true)
  (^void info [_ ^String message] (print-log-message logger-name :info false message nil nil))
  (^void info [_ ^String format ^Object arg] (print-log-message logger-name :info true format [arg] nil))
  (^void info [_ ^String format ^Object arg1 ^Object arg2] (print-log-message logger-name :info true format [arg1 arg2] nil))
  (^void info [_ ^String format ^objects args] (print-log-message logger-name :info true format args nil))
  (^void info [_ ^String message ^Throwable e] (print-log-message logger-name :info false message nil e))

  (isWarnEnabled [_] true)
  (^void warn [_ ^String message] (print-log-message logger-name :warn false message nil nil))
  (^void warn [_ ^String format ^Object arg] (print-log-message logger-name :warn true format [arg] nil))
  (^void warn [_ ^String format ^Object arg1 ^Object arg2] (print-log-message logger-name :warn true format [arg1 arg2] nil))
  (^void warn [_ ^String format ^objects args] (print-log-message logger-name :warn true format args nil))
  (^void warn [_ ^String message ^Throwable e] (print-log-message logger-name :warn false message nil e))

  (isErrorEnabled [_] true)
  (^void error [_ ^String message] (print-log-message logger-name :error false message nil nil))
  (^void error [_ ^String format ^Object arg] (print-log-message logger-name :error true format [arg] nil))
  (^void error [_ ^String format ^Object arg1 ^Object arg2] (print-log-message logger-name :error true format [arg1 arg2] nil))
  (^void error [_ ^String format ^objects args] (print-log-message logger-name :error true format args nil))
  (^void error [_ ^String message ^Throwable e] (print-log-message logger-name :error false message nil e))

  ;;
  ;; java.lang.Object
  ;; ================
  ;;

  Object
  (toString [_]
    (str "tukki.log.slf4j.logger.Logger[" logger-name "]")))


(defn make-logger [logger-name]
  (->Logger logger-name))
