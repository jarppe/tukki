(ns test.run-scenario
  (:import (java.util.concurrent Executors
                                 CountDownLatch
                                 TimeUnit)))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defonce init
  (do (java.util.Locale/setDefault java.util.Locale/US)
      (doto (Executors/newVirtualThreadPerTaskExecutor)
        (set-agent-send-executor!)
        (set-agent-send-off-executor!))))


(defn logf [f & args]
  (.println System/err (apply format f args))
  nil)


(defn get-test-call [scenario]
  (let [scenario-ns (symbol (str "test.scenario." scenario))]
    (require scenario-ns)
    (let [init      @(ns-resolve scenario-ns 'init)
          test-call @(ns-resolve scenario-ns 'test-call)]
      ;; Init scenario: 
      (init)
      ;; Return test function
      test-call)))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn speed-test [{:syms [scenario workers calls]}]
  (logf "speed-test: start: scenario=[%s], workers=[%d], calls=[%d]" scenario workers calls)
  (let [test-call   (get-test-call scenario)
        start-latch (CountDownLatch. 1)
        ready-latch (CountDownLatch. workers)
        done-latch  (CountDownLatch. workers)
        executor    (Executors/newVirtualThreadPerTaskExecutor)
        worker      (fn []
                      (.countDown ready-latch)      ;; Report worker is ready
                      (.await start-latch)          ;; Wait for start signal
                      (dotimes [_ calls]            ;; Do the test calls
                        (test-call))
                      (.countDown done-latch))]     ;; Mark worker as done
    ;; Warm-up:
    (dotimes [_ 1000]
      (test-call))
    ;; Create workers:
    (dotimes [_ workers]
      (.execute executor worker))
    ;; Wait for workers to be ready:
    (.await ready-latch)
    ;; Actual test:
    (let [start-time (System/currentTimeMillis)
          _          (do (.countDown start-latch)  ;; Give workers start signal
                         (.await done-latch))      ;; Wait for workers to complete
          end-time   (System/currentTimeMillis)
          test-time  (- end-time start-time)]
      ;; Shutdown everything
      (.shutdownNow executor)
      (.awaitTermination executor 1000 TimeUnit/MILLISECONDS)
      (logf "speed-test: done: impl=[%s], workers=[%d], calls=[%d], time=[%d]"
            scenario
            workers
            calls
            test-time))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn mem-test [{:syms [scenario workers duration pause]}]
  (logf "mem-test: start: scenario=[%s], workers=[%d], duration=[%ds]"
        scenario
        workers
        duration)
  (let [test-call   (get-test-call scenario)
        start-latch (CountDownLatch. 1)
        ready-latch (CountDownLatch. workers)
        executor    (Executors/newVirtualThreadPerTaskExecutor)
        worker      (fn []
                      (.countDown ready-latch)      ;; Report worker is ready
                      (.await start-latch)          ;; Wait for start signal
                      ;; Do the test calls
                      (try
                        (while true
                          (test-call)
                          (Thread/sleep pause))
                        (catch java.lang.InterruptedException _)))]

    ;; Create workers:
    (dotimes [_ workers]
      (.execute executor worker))
    (.await ready-latch)               ;; Wait for workers to be ready
    (let [start-time (System/currentTimeMillis)]
      (.countDown start-latch)         ;; Give workers start signal
      (Thread/sleep (* duration 1000)) ;; Wait for requested duration
      (.shutdownNow executor)          ;; Shutdown everything
      (.awaitTermination executor 1000 TimeUnit/MILLISECONDS)
      (logf "mem-test: done: scenario=[%s], workers=[%d], duration=[%ds], time=[%.1fs]"
            scenario
            workers
            duration
            (-> (- (System/currentTimeMillis) start-time)
                (/ 1000.0))))))


(comment
  (speed-test {'scenario "tukki"
               'workers  "10"
               'calls    "10"})
  (mem-test {'scenario "tukki"
             'workers  "10"
             'duration "10"
             'pause    "1000"})
  ;
  )