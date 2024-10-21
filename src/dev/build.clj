(ns build
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as deploy]))


(def lib-name 'jarppe/tukki)

(def lib-id (symbol (str "io.github." lib-name)))
(def src "./src/main")
(def resources "./resources")
(def compile-ns 'tukki.log.slf4j.log-service-provider)


(def target "./target")
(def classes (str target "/classes"))
(def jar-file (str target "/tukki.jar"))
(def pom-file (str target "/pom.xml"))


(defn get-version []
  (let [f (io/file "version.edn")
        v (-> (slurp f)
              (edn/read-string)
              (update :build inc))]
    (with-open [out (-> (io/file "version.edn")
                        (io/writer))]
      (.write out (pr-str v)))
    (str (:major v) "."
         (:minor v) "."
         (:build v))))


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
          :jar-file  jar-file})
  (let [version (get-version)]
    (b/write-pom {:basis    basis
                  :lib      lib-id
                  :target   target
                  :version  version
                  :scm      {:url                 (str "https://github.com/" lib-name)
                             :connection          (str "scm:git:git://github.com/" lib-name ".git")
                             :developerConnection (str "scm:git:ssh://git@github.com/" lib-name ".git")
                             :tag                 version}
                  :pom-data [[:licenses [:license
                                         [:name "Eclipse Public License 1.0"]
                                         [:url "https://opensource.org/license/epl-1-0/"]
                                         [:distribution "repo"]]]]})
    (println "built jar: version" version)))

;;
;; Tools API:
;;


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn compile-all []
  (doto (b/create-basis)
    (clean)
    (compile-classes)))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn build-all [_]
  (doto (b/create-basis)
    (clean)
    (compile-classes)
    (build-jar)))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn deploy [_]
  (when-not (and (System/getenv "CLOJARS_USERNAME")
                 (System/getenv "CLOJARS_PASSWORD"))
    (println "error: missing env: CLOJARS_USERNAME and CLOJARS_PASSWORD are required")
    (System/exit 1))
  (build-all nil)
  (deploy/deploy {:artifact       jar-file
                  :pom-file       pom-file
                  :installer      :remote
                  :sign-releases? false})
  (let [version (->> (io/file "version.edn")
                     (slurp)
                     (edn/read-string)
                     ((juxt :major :minor :build))
                     (str/join "."))]
    (println "deployed:" (str lib-id " {:mvn/version \"" version "\"}"))))
