(ns tukki.log.impl
  (:require [clojure.string :as str]
            [tukki.log.state :as state])
  (:import (java.io OutputStream
                    PrintStream
                    FileOutputStream
                    FileDescriptor
                    BufferedOutputStream
                    ByteArrayOutputStream)
           (java.nio.charset StandardCharsets)
           (java.time ZoneId
                      ZoneOffset)
           (java.time.format DateTimeFormatter)
           (org.slf4j.event Level)))


(set! *warn-on-reflection* true)


;;
;; The output stream for log records:
;;


(defonce ^OutputStream out (-> (FileOutputStream. FileDescriptor/out)
                               (BufferedOutputStream.)))


;;
;; Internal API:
;;


;; Retrurn the current configured logging level for given logger.


(defn get-level-for [logger-name]
  (or (->> state/state
           :levels
           (sort-by (comp - count key))
           (some (fn [[k v]]
                   (when (str/starts-with? logger-name k)
                     v))))
      (:root-level state/state)))


;; Map log level from keyword to int. Use the same int values as the SLF4J does for
;; easy interop.


(def level->int "Map log level to int"
  {:trace (-> Level/TRACE (.toInt))
   :debug (-> Level/DEBUG (.toInt))
   :info  (-> Level/INFO  (.toInt))
   :warn  (-> Level/WARN  (.toInt))
   :error (-> Level/ERROR (.toInt))
   :fatal (-> Level/ERROR (.toInt) (+ 10))})  ;; SLF4J does not have level FATAL, use ERROR + 10


;;
;; Returns truthy when the logging is enabled for given logger name on given log level.
;;


(defn logger-enabled-for? [logger-name level]
  (>= (level->int level)
      (level->int (get-level-for logger-name))))


;;
;; Called from tukki logging macros. Sends the log information to `out` using
;; the format defined in configured appender. Not intended to be called directly by
;; application code.
;;


(defn throwable? [v]
  (when (instance? Throwable v)
    v))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn print-log-message [appender ns line level args]
  ;; TODO: Would StringWriter be better appendable?
  (let [ex     (some throwable? args)
        args   (if ex (remove throwable? args) args)
        buffer (ByteArrayOutputStream. 256)]
    (doto (PrintStream. buffer false StandardCharsets/UTF_8)
      (appender (java.time.Instant/now) ns line level args ex))
    (.write out (.toByteArray buffer))
    nil))


;;
;; Initialize tukki config.
;;


(defn get-init-config []
  (let [get-config  (fn [config-key [default-value parse-str]]
                      (let [value (or (System/getenv (str "TUKKI_" (-> (if (qualified-keyword? config-key)
                                                                         (str (namespace config-key) "_" (name config-key))
                                                                         (name config-key))
                                                                       (str/replace "-" "_")
                                                                       (str/upper-case))))
                                      (System/getProperty (str "tukki." (if (qualified-keyword? config-key)
                                                                          (str (namespace config-key) "." (name config-key))
                                                                          (name config-key)))))]
                        (if value
                          (parse-str value)
                          default-value)))
        ->boolean   {"true"  true
                     "false" false}
        init-config (->> {:root-level        [:error keyword]
                          :appender          ['tukki.log.appender.simple/append-event symbol]
                          :simple/color      [true ->boolean]
                          :simple/ts         [nil (fn [value]
                                                    (DateTimeFormatter/ofPattern (get {"time"     "HH:mm:ss.SSS"
                                                                                       "datetime" "yyyy-MM-dd HH:mm:ss.SSS"}
                                                                                      value
                                                                                      value)))]
                          :simple/tz         [ZoneOffset/UTC (fn [value] (ZoneId/of value))]
                          :simple/mdc        [true ->boolean]
                          :simple/trim-ex-ns [true ->boolean]}
                         (reduce-kv (fn [acc k v]
                                      (assoc acc k (get-config k v)))
                                    {}))
        levels      (-> (into {} (concat (->> (System/getenv)
                                              (filter (fn [[k]] (str/starts-with? k "TUKKI_LEVEL_")))
                                              (map (fn [[k v]]
                                                     [(-> (subs k (count "TUKKI_LEVEL_"))
                                                          (str/replace "__" "-")
                                                          (str/replace "_" ".")
                                                          (str/lower-case))
                                                      v])))
                                         (->> (System/getProperties)
                                              (filter (fn [[k]] (str/starts-with? k "tukki.level.")))
                                              (map (fn [[k v]]
                                                     [(subs k (count "tukki.level."))
                                                      v])))))
                        (update-vals (let [known-level (set (keys level->int))]
                                       (fn [level]
                                         (-> level
                                             (keyword)
                                             (known-level)
                                             (or (throw (ex-info (str "tukki config error: unknown log level: " level)
                                                                 {:level level}))))))))]
    (assoc init-config :levels levels)))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defonce -init-config
  (let [init-config (get-init-config)]
    (requiring-resolve (:appender init-config))
    (alter-var-root #'state/state (constantly init-config))))


;;
;; Initialize logging state. Build the background threads for log output flushing and the shutdown hook.
;; Called when this ns is required the first time.
;;
;; This is intended for development of the tukki library. Do not call this from application side.
;;


(defn -init []
  (let [flusher       (-> (Thread/ofVirtual)
                          (.name "tukki-log-flusher")
                          (.start (fn []
                                    (try
                                      (while true
                                        (Thread/sleep 200)
                                        (.flush out))
                                      (catch InterruptedException _)))))
        shutdown-hook (-> (Thread/ofVirtual)
                          (.name "tukki-log-flush-on-shutdown")
                          (.unstarted (fn []
                                        (.interrupt flusher)
                                        (.flush out))))]
    (.addShutdownHook (Runtime/getRuntime) shutdown-hook)
    (fn []
      (.interrupt flusher)
      (.removeShutdownHook (Runtime/getRuntime) shutdown-hook)
      (.flush out))))


;;
;; Initialize tukki background threads. The -shutdown-state var has a function that executes the
;; shutdown, stopping the threads and removing the shutdown hook etc.
;;
;; This is intended for development of the tukki library. Do not call this from application side.
;;

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defonce -shutdown-state (-init))
