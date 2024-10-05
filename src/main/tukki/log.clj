(ns tukki.log
  "Tukki logging library"
  (:require [tukki.log.state :as state]
            [tukki.log.impl :as impl]
            [tukki.log.mdc :as mdc]))


(set! *warn-on-reflection* true)


(defmacro -log
  "Internal macro to emit the call to impl/print-log-message if the logging is enabled for given ns."
  [the-ns line level message & args]
  (let [the-ns-name (-> the-ns (ns-name) (name))]
    (when (impl/logger-enabled-for? the-ns-name level)
      (list `impl/print-log-message
            (:appender state/state)
            the-ns-name
            line
            level
            (apply vector message args)))))


(defmacro log   "Log message using given log level" [level message & args] (list* `-log *ns* (-> &form (meta) :line) level  message args))
(defmacro trace "Log message using trace level"     [message & args]       (list* `-log *ns* (-> &form (meta) :line) :trace message args))
(defmacro debug "Log message using debug level"     [message & args]       (list* `-log *ns* (-> &form (meta) :line) :debug message args))
(defmacro info  "Log message using info level"      [message & args]       (list* `-log *ns* (-> &form (meta) :line) :info  message args))
(defmacro warn  "Log message using warn level"      [message & args]       (list* `-log *ns* (-> &form (meta) :line) :warn  message args))
(defmacro error "Log message using error level"     [message & args]       (list* `-log *ns* (-> &form (meta) :line) :error message args))
(defmacro fatal "Log message using error level"     [message & args]       (list* `-log *ns* (-> &form (meta) :line) :fatal message args))


(defmacro with-mdc
  "Append given bindings to the MDC context"
  [bindings & body]
  `(binding [mdc/*mdc* (merge mdc/*mdc* ~(->> (partition 2 bindings)
                                              (reduce (fn [acc [k v]]
                                                        (assoc acc (str k) v))
                                                      {})))]
     ~@body))


(comment
  (macroexpand-1 '(error "hello" :world (+ 1 2)))
  ;; => (tukki.log/-log #namespace[tukki.log]
  ;;                    46
  ;;                    :error
  ;;                    "hello"
  ;;                    :world
  ;;                    (+ 1 2)) 

  (macroexpand '(debug "hello" :world (+ 1 2)))
  ;; => nil

  (require '[tukki.log.config :as config])
  (config/set-log-appender! :dev)
  (config/set-log-level! *ns* :info)

  (info "hello" :world (+ 1 2))
  ;; prints:
  ;; 11:50:15.722 info  [t.log:61] hello :world 3 

  (debug "hello" :world (+ 1 2))
  ;; nothing printed

  (config/set-log-appender! :prod)

  (info "hello" :world (+ 1 2))
  ;; prints:
  ;; {"ts":"2024-10-04T08:54:39.290918Z","level":"info","ns":"tukki.log","line":70,"message":["hello","world",3],"mdc":null,"ex":null} 

  (macroexpand-1 '(with-mdc [foo "fofo"
                             bar "baba"]
                    (info "hello")))
  ;; => (binding [tukki.log.mdc/*mdc* (merge tukki.log.mdc/*mdc* 
  ;;                                         {"foo" "fofo",
  ;;                                          "bar" "baba"})]
  ;;      (info "hello"))

  (with-mdc [foo "fofo"
             bar "baba"]
    (info "hello"))
  ;; prints:
  ;; {"ts":"2024-10-04T08:56:19.419374Z","level":"info","ns":"tukki.log","line":84,"message":["hello"],"mdc":{"foo":"fofo","bar":"baba"},"ex":null}
  ;;                                                                                                          \------------------------/
  ;;                                                                                                                     |
  ;; Notice the context:  ------------------------------------------------------------------------------------------------

  (config/set-log-appender! :dev)

  (try
    (throw (ex-info "oh no" {:message "this is bad"}))
    (catch Exception e
      (info e "something broken")))
  ;; prints:
  ;; 11:56:50.861 info  [t.log:96] something broken 
  ;; clojure.lang.ExceptionInfo: "oh no" {:message "this is bad"}
  ;;                                ?:94 tukki.log/eval11571
  ;;                                ?:93 tukki.log/eval11571
  ;;                  Compiler.java:7700 clojure.lang.Compiler.eval
  ;;                  Compiler.java:7655 clojure.lang.Compiler.eval
  ;;                       core.clj:3232 clojure.core/eval
  ;; ...

  (info "something broken" (ex-info "oh no" {:message "this is bad"}))
  ;; prints the same as above
  ;; Note: this api allows the exception to be in any position.

  ;
  )

