(defproject net.drib/mrhyde "0.5.0"
  :description "mrhyde: cljs <-> js interop"
  :url "https://github.com/dribnet/mrhyde"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :min-lein-version "2.0.0"
  :source-paths ["src/clj" "src/cljs"]

  :plugins [[lein-cljsbuild "0.3.2"]]

  :cljsbuild {:builds [{:source-paths ["src/cljs" "src/clj"]
                        :jar true}

                       ; tests - 3 different optimizations
                       {:source-paths ["src/cljs" "src/clj" "test/cljs"]
                        :compiler  {:optimizations :whitespace
                                    :pretty-print true
                                    :print-input-delimiter true
                                    :output-to "public/out/mrhyde_test_whitespace.js"}}
                       {:source-paths ["src/cljs" "src/clj" "test/cljs"]
                        :compiler  {:optimizations :simple
                                    :pretty-print true
                                    :print-input-delimiter true
                                    :output-to "public/out/mrhyde_test_simple.js"}}
                       {:source-paths ["src/cljs" "src/clj" "test/cljs"]
                        :compiler  {:optimizations :advanced
                                    :externs [
                                      "public/d3/d3-externs.js"
                                      "public/dummylib/dummylib-externs.js"
                                    ]
                                    :pretty-print true
                                    :print-input-delimiter true
                                    :output-to "public/out/mrhyde_test_advanced.js"}}
                                    ]})
