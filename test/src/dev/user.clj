(ns user
  (:require [clojure.tools.namespace.repl :as ctn]))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn reset []
  (ctn/refresh {:after 'user/reloaded}))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn reloaded []
  (println "-- Reloaded"))
