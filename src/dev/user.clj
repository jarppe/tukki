(ns user
  (:require [clojure.tools.namespace.repl :as ctn]))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn reset []
  (ctn/disable-reload! (clojure.lang.Namespace/findOrCreate 'tukki.log.state))
  (ctn/refresh {:after 'user/reloaded}))



#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn reloaded []
  (println "-- Reloaded"))


(comment
  (require '[tukki.log :as log])

  (require 'tukki.log.config)
  (tukki.log.config/set-log-level! "user" :info)
  (tukki.log.config/set-log-level! "foo" :info)

  (log/info "jiihaa")

  (let [LOG      (org.slf4j.LoggerFactory/getLogger "foo.bar.Boz")
        listener (Object.)
        x        (java.io.IOException. "Oh no")]
    (.info LOG "Failure while notifying listener {}" listener x))
  ;
  )