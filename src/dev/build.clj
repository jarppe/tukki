(ns build
  (:require [clojure.tools.build.api :as b]))

(def src "./src/main")
(def target "./target")
(def classes (str target "/classes"))
(def compile-ns 'tukki.log.slf4j.log-service-provider)
(def resources "./resources")


(defn clean [_]
  (b/delete {:path target}))


(defn compile-classes [basis]
  (b/compile-clj {:basis        basis
                  :ns-compile   [compile-ns]
                  :class-dir    classes
                  :compile-opts {:elide-meta     [:doc :file :line :added]
                                 :direct-linking true}
                  :bindings     {#'clojure.core/*assert* false}})
  (b/copy-dir {:src-dirs   [resources]
               :target-dir classes}))


(defn build-jar [basis]
  (b/compile-clj {:basis        basis
                  :ns-compile   [compile-ns]
                  :class-dir    classes
                  :compile-opts {:elide-meta     [:doc :file :line :added]
                                 :direct-linking true}
                  :bindings     {#'clojure.core/*assert* false}})
  (b/copy-dir {:src-dirs   [resources]
               :target-dir classes})
  (b/copy-dir {:src-dirs   [src]
               :target-dir classes})
  (b/jar {:class-dir classes
          :jar-file  (str target "/tukki.jar")})
  (b/write-pom {:basis   basis
                :lib     'jarppe.tukki/tukki
                :version "0.0.0-SNAPSHOT"
                :scm     {:url                 "https://github.com/my-username/my-cool-lib"
                          :connection          "scm:git:git://github.com/my-username/my-cool-lib.git"
                          :developerConnection "scm:git:ssh://git@github.com/my-username/my-cool-lib.git"
                          :tag                 "0.0.0-SNAPSHOT"}
                :target  target}))


;;
;; Public API:
;;


(defn compile-all []
  (doto (b/create-basis)
    (clean)
    (compile-classes)))


(defn build-all [_]
  (doto (b/create-basis)
    (clean)
    (compile-classes)
    (build-jar)))


(comment
  (build-all nil)
  ;
  )