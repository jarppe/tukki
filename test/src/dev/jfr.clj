(ns jfr
  (:import (java.io File)
           (java.nio.file Path)
           (jdk.jfr.consumer RecordingFile
                             RecordedEvent)))


(set! *warn-on-reflection* true)


;;
;; ------------------------------------------------------------------
;; JFR processing:
;; ------------------------------------------------------------------
;;


(defn jfr-open-recording ^RecordingFile [^Path p]
  (RecordingFile. p))


(defn jfr-events-seq [^RecordingFile rec]
  (when (.hasMoreEvents rec)
    (cons (.readEvent rec)
          (lazy-seq (jfr-events-seq rec)))))


;;
;; ------------------------------------------------------------------
;; Parsing JFR events:
;; ------------------------------------------------------------------
;;


(defn parse-event [^RecordedEvent event]
  (case (-> event (.getEventType) (.getName))
    "jdk.GCHeapConfiguration" {:type      :mem
                               :ts        (-> event (.getStartTime) (.toEpochMilli))
                               :min-size  (-> event (.getLong "minSize"))
                               :max-size  (-> event (.getLong "maxSize"))
                               :init-size (-> event (.getLong "initialSize"))}
    "jdk.GCHeapSummary" {:type :heap
                         :ts   (-> event (.getStartTime) (.toEpochMilli))
                         :heap (-> event (.getLong "heapUsed"))}
    "jdk.GarbageCollection" {:type          :gc
                             :ts            (-> event (.getStartTime) (.toEpochMilli))
                             :sum-of-pauses (-> event (.getLong "sumOfPauses"))
                             :longest-pause (-> event (.getLong "longestPause"))}
    nil))


(defn parse-recording [^File recording]
  (with-open [rec (jfr-open-recording (.toPath recording))]
    (->> (jfr-events-seq rec)
         (keep parse-event)
         (sort-by :ts)
         (doall))))

(comment

  (def recording (Path/of "report/ctl-lob-dyn.jfr" (make-array String 0)))

  (->> (parse-recording recording)
       (take 10))
  ;; => ({:type :mem, :ts 1726242203184, :min-size 8388608, :max-size 268435456, :init-size 268435456}
  ;;     {:type :heap, :ts 1726242203211, :heap 24117248}
  ;;     {:type :gc, :ts 1726242203211, :sum-of-pauses 2545709, :longest-pause 2545709}
  ;;     {:type :heap, :ts 1726242203213, :heap 4194304}
  ;;     {:type :heap, :ts 1726242203492, :heap 31457280}
  ;;     {:type :gc, :ts 1726242203492, :sum-of-pauses 2977625, :longest-pause 2977625}
  ;;     {:type :heap, :ts 1726242203495, :heap 6662032}
  ;;     {:type :gc, :ts 1726242203495, :sum-of-pauses 1200792, :longest-pause 1194792}
  ;;     {:type :heap, :ts 1726242203495, :heap 6662032}
  ;;     {:type :heap, :ts 1726242203498, :heap 6662032})
  )


;; https://www.chriswhocodes.com/jfr_jdk20.html
;;
;; jdk.GCHeapSummary
;; =================
;; Name              Type          ContentType     Label             Description
;; -----------------------------------------------------------------------------
;; gcId              uint                          GC Identifier
;; when              GCWhen                        When
;; heapSpace         VirtualSpace                  Heap Space
;; heapUsed          ulong         bytes           Heap Used         Bytes allocated by objects in the heap
;;
;; jdk.G1HeapSummary
;; =================
;; Name              Type          ContentType     Label             Description
;; -----------------------------------------------------------------------------
;; gcId              uint                          GC Identifier
;; when              GCWhen                        When
;; edenUsedSize      ulong         bytes           Eden Used Size
;; edenTotalSize     ulong         bytes           Eden Total Size
;; survivorUsedSize  ulong         bytes           Survivor Used Size
;; numberOfRegions   uint                          Number of Regions
