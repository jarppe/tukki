(ns tukki.log.appender.simple
  (:require [clojure.string :as str]
            [tukki.log.mdc :as mdc]
            [tukki.log.state :as state])
  (:import (java.time ZoneId)
           (java.time.format DateTimeFormatter)))


(defn append-inst [^Appendable out ^java.time.Instant inst]
  (let [date-time-formatter (:simple/ts state/state)
        date-time-zone      (:simple/tz state/state)]
    (when date-time-formatter
      (doto out
        (.append (->> (.atZone inst ^ZoneId date-time-zone)
                      (.format ^DateTimeFormatter date-time-formatter)))
        (.append " ")))))


(defn append-mdc [^Appendable out]
  (when (:simple/mdc state/state)
    (let [mdc mdc/*mdc*]
      (when mdc
        (doto out
          (.append " ")
          (.append (pr-str mdc)))))))


(def clojure-name-specials {"QMARK" "?"
                            "BANG"  "!"
                            "PLUS"  "+"
                            "GT"    ">"
                            "LT"    "<"
                            "EQ"    "="
                            "STAR"  "*"
                            "SLASH" "/"
                            "COLON" ":"})


(defn pretty-print-clj-class ^String [class-name]
  (-> class-name
      (str/replace "$" "/")
      (str/replace #"_([A-Z]{2,5})_" (fn [[_ special]]
                                       (clojure-name-specials special special)))
      (str/replace "_" "-")
      (str/replace #"\/fn?--\d+.*" "[fn]")))


(defn append-ex-stacktrace-element [^Appendable out ^java.lang.StackTraceElement ste]
  (let [file   (-> ste (.getFileName))
        line   (-> ste (.getLineNumber))
        class  (-> ste (.getClassName))
        method (-> ste (.getMethodName))]
    (.append out (format "%35s" (str (if (= file "NO_SOURCE_FILE")
                                       "?"
                                       file)
                                     ":"
                                     line)))
    (.append out " ")
    (.append out (if (#{"invokeStatic" "invoke"} method)
                   (pretty-print-clj-class class)
                   (str class "." method)))
    (.append out "\n"))
  out)


(defn append-ex-info [^Appendable out ^Throwable ex nested?]
  (.append out (-> ex (.getClass) (.getName)))
  (.append out ": ")
  (.append out (-> ex (.getMessage) (pr-str)))
  (when-let [data (ex-data ex)]
    (.append out " ")
    (.append out (pr-str data)))
  (.append out "\n")
  (if nested?
    (append-ex-stacktrace-element out (-> ex (.getStackTrace) (aget 0)))
    (doseq [ste (-> ex (.getStackTrace))]
      (append-ex-stacktrace-element out ste)))
  (.append out "\n")
  out)


(defn append-ex [^Appendable out ^Throwable ex]
  (when ex
    (let [cause (.getCause ex)]
      (append-ex out cause)
      (append-ex-info out ex (some? cause)))))


(defn append-ns [^Appendable out the-ns-name line]
  (let [the-ns-name (if-not (:simple/trim-ex-ns state/state)
                      the-ns-name
                      (let [ns-path (str/split the-ns-name #"\.")
                            last-ns (last ns-path)]
                        (str (str/join "." (map (fn [n] (subs n 0 1)) (butlast ns-path)))
                             "."
                             last-ns)))]
    (.append out " [")
    (.append out ^String the-ns-name)
    (when (and line (pos? line))
      (.append out ":")
      (.append out (str line)))
    (.append out "] ")))


(defn append-args [^Appendable out args]
  (doseq [arg args]
    (doto out
      (.append (str arg))
      (.append " "))))


(def level->name {:trace "trace"
                  :debug "debug"
                  :info  "info "
                  :warn  "warn "
                  :error "error"
                  :fatal "fatal"})


(def level->ansi-color {:trace "\033[90m"
                        :debug "\033[90m"
                        :info  "\033[32m"
                        :warn  "\033[93m"
                        :error "\033[91m"
                        :fatal "\033[91m"})


(def ^String ansi-reset "\033[0m")


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn append-event [^Appendable out ^java.time.Instant inst ^String the-ns-name line level args ex]
  (let [^String color       (if (:simple/color state/state) (level->ansi-color level) "")
        ^String color-reset (if (:simple/color state/state) ansi-reset "")
        ^String level-name  (-> level level->name)]
    (doto out
      (.append color)
      (append-inst inst)
      (.append level-name)
      (append-ns the-ns-name line)
      (append-args args)
      (.append color-reset)
      (append-mdc)
      (.append "\n")
      (append-ex ex))))


(comment
  (let [out (java.io.StringWriter.)]
    (append-event out (java.time.Instant/now) "foo.bar.boz" 1234 :info ["hello"] nil)
    (str out))
  ;;=> "info  [f.b.boz:1234] hello \n"

  (do (defn foo-bar []
        (throw (ex-info "oh no" {:bad true})))
      (defn bar!foo []
        (try
          (foo-bar)
          (catch Exception e
            (throw (java.io.IOException. "looks sus" e)))))
      (defn +bar:boz []
        (let [f (fn [] (bar!foo))]
          (try
            (f)
            (catch Exception e
              (throw (ex-info "real bad" {:bar "9k"} e))))))
      (try
        (+bar:boz)
        (catch Exception e
          (append-ex *out* e))))
  ;
  )