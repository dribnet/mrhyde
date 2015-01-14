(ns mrhyde.test
    (:require [mrhyde.tester :refer [add-test run-all-tests]]
              [mrhyde.mrhyde :refer [hyde? has-cache? from-cache]]
              [mrhyde.core :refer [bootstrap]]
              [mrhyde.extend-js :refer [assoc-in! update-in!]]
              [mrhyde.typepatcher :refer [
                              recurse-from-hyde-cache
                              patch-known-sequential-types]]
              [mrhyde.funpatcher :refer [
                              patch-return-value-to-clj
                              patch-args-keyword-to-fn
                              patch-args-clj-to-js]]
      ))

;(def DummyLib (this-as ct (aget ct "DummyLib")))
(def DummyLib (js-obj))

(defn init []
  ; patch the dummy library
  (if DummyLib (do
    ; standard - patch vectors and maps
    (bootstrap)
    ; patch all seqs to also be read-only arrays for javascript interop
    (patch-known-sequential-types)

    ;;; start patching library function calls

    ; force params 0 and 2 to be js args
    (patch-args-clj-to-js DummyLib "wrapArgs0and2" 0 2)

    ; force these functions to return cljs objects
    (patch-return-value-to-clj DummyLib "wrapReturnArgsIntoArray")
    (patch-return-value-to-clj DummyLib "wrapReturnArgsIntoObject")

    ; coerce param into function if it's a keyword
    (patch-args-keyword-to-fn DummyLib "wrapCall0on1" 0)

    ; patch both ways (chaining)
    (patch-args-clj-to-js DummyLib "wrapArraysInAndOut")
    (patch-return-value-to-clj DummyLib "wrapArraysInAndOut")
  ))
)

; is js stupid? http://stackoverflow.com/a/5115066/1010653
(defn js-arrays-equal [a b]
  (not (or (< a b) (< b a))))

(defn ^:export launch [s]

  (init)

  (add-test "js access cljs vector as array"
    (fn []
      (let [v [1 2 3 4 5]]
        (assert (= 5 (.-length v)))
        (assert (= 1 (aget v 0)))
        (assert (= 3 (aget v "2")))
        (assert (= 5 (aget v 4)))
        (assert (= js/undefined (aget v 5)))
        (assert (= js/undefined (aget v -1)))
        )))

  (add-test "js access lazy seq elements as array"
    (fn []
      (let [l (for [x (range 1 100) :when (odd? x)] x)]
        ; (.log js/console (str l))
        (assert (= 1 (aget l 0)))
        (assert (= 21 (aget l 10)))
        (assert (= js/undefined (aget l 70)))
        (assert (= 50 (.-length l)))
        )))

  (add-test "js access lazy seq of vectors as array of arrays"
    (fn []
      (let [l (for [x (range 3) y (range 3) :when (not= x y)] [x y])]
        ;; l is now: ([0 1] [0 2] [1 0] [1 2] [2 0] [2 1])
        (assert (= [0 1] (aget l 0)))
        (assert (= [0 2] (aget l 1)))
        (assert (= [2 1] (aget l 5)))
        (assert (= 2 (aget (aget l 3) 1)))
        ; (assert (js-arrays-equal (array 2 1) (aget l 5)))
        (assert (= js/undefined (aget l 7)))
        (assert (= js/undefined (aget (aget l 0) 3)))
        (assert (= 6 (.-length l)))
        )))

  (add-test "js access maps as object fields"
    (fn []
      (let [m {:one 1 :two 2 :three 3}]
        (assert (= 1 (.-one m)))
        (assert (= 2 (aget m "two")))
        (assert (= 3 (.-three m)))
        (assert (= js/undefined (.-four m)))
        )))

  (add-test "hokey js-arrays-equal helper function"
    (fn []
        (assert (js-arrays-equal (array 0 1) (array 0 1)))
        (assert (not (js-arrays-equal (array 0 1) (array 1 0))))))

  (add-test "patch-args-clj-to-js selectively"
    (fn []
      (let [v [1,2]
            m {:one 1, :two 2}
            [r0 r1 r2] (.wrapArgs0and2 js/DummyLib  v m m)]

        ; (.log js/console r0)
        ; (.log js/console (array 1 2))

        ; check equality where you can
        (assert (js-arrays-equal (array 1 2) r0))
        (assert (= m r1))
        (assert (not= m r2))

        ; would work because of map to obj mapping
        ; (assert (= 1 (aget r1 "one")))
        ; (assert (goog.object.containsKey r1 "one"))
        ; it is actually a cljs object
        (assert (satisfies? ILookup r1))
        (assert (= 1 (:one r1)))

        ; will work because object returned unchanged
        (assert (= 1 (aget r2 "one")))
        (assert (goog.object.containsKey r2 "one"))
        ; and is actually now a js object
  ;;?      (assert (not (satisfies? ILookup r2)))
        ; (assert (= 1 (:one r2))) ; <-- error, don't know how to catch right now
        )))

(add-test "patch-return-value-to-clj"
    (fn []
      (let [ra (.wrapReturnArgsIntoArray js/DummyLib 0 1 2)
            rav (.wrapReturnArgsIntoArray js/DummyLib (array 0 1) (array 2 3) (array 4 5))
            ro (.wrapReturnArgsIntoObjectjs/DummyLib 1 2 3)
            rov (.wrapReturnArgsIntoObjectjs/DummyLib (array 0 1) (array 2 3) (array 4 5))]

        ; (.log js/console (str rov))

        ; simple array case
        (assert (= ra [0 1 2]))
        ; but it goes deep
        (assert (= rav [[0 1] [2 3] [4 5]]))

        ; simple object case
        (assert (= ro {"a" 1 "b" 2 "c" 3}))
        ; also goes deep
        (assert (= rov {"a" [0 1] "b" [2 3] "c" [4 5]}))
        )))

(add-test "patch-args-keyword-to-fn selectively"
    (fn []
      (let [m {:one 1 :two 2 :three 3}]

        ; check equality where you can
        (assert (= 1 (.wrapCall0on1 js/DummyLib :one m)))
        (assert (= 3 (.wrapCall0on1 js/DummyLib :three m)))
        (assert (= nil (.wrapCall0on1 js/DummyLib :four m)))
        )))

(add-test "patch everything in to-js and out to-clj"
    (fn []
      (let [ra (.wrapArraysInAndOut js/DummyLib 1 2 3)
            rav (.wrapArraysInAndOut js/DummyLib [0 1] [2 3] [4 5])
            [r1 r2 r3] (.wrapArraysInAndOut js/DummyLib [:a :b :c] [#{:a, :b, :c}] ["a" "b" "c"])]

        ; (.log js/console r2)
        ; (.log js/console (str (r2 0)))
        ; (aset js/window "dbg" r2)
        ; (aset js/window "dbg2" rav)

        ; only array js/array arguments are returned
        (assert (= ra []))
        ; translated into their cljs equivalents
        (assert (= rav [[0 1] [2 3] [4 5]]))
        ; lost in translation
        (assert (= r1 ["a" "b" "c"]))
        ;; set order can change... (assert (= r2 [["a" "b" "c"]]))
        (assert (= r3 ["a" "b" "c"]))
        )))

(add-test "js array modification of seq recoverable with hyde cache"
    (fn []
      (let [v [1 2 3]]
        (assert (hyde? v))
        (assert (not (has-cache? v)))
        (assert (= (v (from-cache v))))
        (let [[ra rv] (.zeroOutFirstArrayElement js/DummyLib v)]
          ; the method thinks it changed the vector
          (assert (= rv 0))
          ; locally the vector remains unchanged
          (assert (= v [1 2 3]))
          ; though all bets are off if you look under the covers
          (assert (= 0 (aget v 0)))
          ; when it hands back a copy that too looks unchanged from cljs
          (assert (= ra [1 2 3]))
          ; but there is an indication there there is new information added
          (assert (has-cache? ra))
          ; and in fact the 'js view' of this structure is available upon request
          (assert (= [0 2 3] (from-cache ra))))
          )))

(add-test "js obj modification of map (existing keys) recoverable with hyde cache"
    (fn []
      (let [m {:one 1, :two 2, :three 3}]
        (assert (hyde? m))
        (assert (not (has-cache? m)))
        (assert (= (m (from-cache m))))
        (let [[rm rv] (.zeroOutMapKeyOne js/DummyLib m)]
          ; the method thinks it changed the map
          (assert (= rv 0))
          ; locally the vector remains unchanged
          (assert (= m {:one 1, :two 2, :three 3}))
          ; though all bets are off if you look under the covers
          (assert (= 0 (aget m "one")))
          ; when it hands back a copy that too looks unchanged from cljs
          (assert (= rm {:one 1, :two 2, :three 3}))
          ; but there is an indication there there is new information added
          (assert (has-cache? rm))
          ; and in fact the 'js view' of this structure is available upon request
          (assert (= {:one 0, :two 2, :three 3} (from-cache rm)))
      ))))

(add-test "js obj modification of map (new keys) recoverable with hyde cache"
    (fn []
      (let [m {:one 1, :two 2, :three 3}]
        (assert (hyde? m))
        (assert (not (has-cache? m)))
        (assert (= (m (from-cache m))))
        (let [[rm rv] (.zeroOutMapKeyTen js/DummyLib m)]
          ; (-> js/fdebug (set! m))
          ; (-> js/gdebug (set! rm))
          ; (-> js/has (set! has-cache?))
          ; the method thinks it added to the map
          (assert (= rv 0))
          ; locally the vector remains unchanged
          (assert (= m {:one 1, :two 2, :three 3}))
          ; though all bets are off if you look under the covers
          (assert (= 0 (aget m "ten")))
          ; when it hands back a copy that too looks unchanged from cljs
          (assert (= rm {:one 1, :two 2, :three 3}))
          ; but there is an indication there there is new information added
          (assert (has-cache? rm))
          ; and in fact the 'js view' of this structure is available upon request
          (assert (= {:one 1, :two 2, :three 3, :ten 0} (from-cache rm)))
        ))))

(add-test "js obj modification of map (new and existing keys) recoverable with hyde cache"
    (fn []
      (let [m {:one 1, :two 2, :three 3}]
        (assert (hyde? m))
        (assert (not (has-cache? m)))
        (assert (= (m (from-cache m))))
        (let [[rm1 rv1] (.zeroOutMapKeyOne js/DummyLib m)
              [rm2 rv2] (.zeroOutMapKeyTen js/DummyLib rm1)]
          ; remote additons to the maps
          (assert (= rv1 0))
          (assert (= rv2 0))
          ; locally the vector remains unchanged
          (assert (= m {:one 1, :two 2, :three 3}))
          ; though all bets are off if you look under the covers
          (assert (= 0 (aget m "one")))
          (assert (= 0 (aget m "ten")))
          ; when it hands back a copy that too looks unchanged from cljs
          (assert (= rm1 {:one 1, :two 2, :three 3}))
          (assert (= rm2 {:one 1, :two 2, :three 3}))
          ; but there is an indication there there is new information added
          (assert (has-cache? rm1))
          (assert (has-cache? rm2))
          ; and in fact the 'js view' of this structure is available upon request
          (assert (= {:one 0, :two 2, :three 3, :ten 0} (from-cache rm1)))
          (assert (= {:one 0, :two 2, :three 3, :ten 0} (from-cache rm2)))
        ))))

(add-test "deep recovery of hyde cache"
    (fn []
      (let [d [
              {:bye 1, :now 2, :zero 3}
              {:two [], :three 3}
              {:one 1, :two 2, :vec ["fool" 1 [:zerobound]]}
            ]]
        (assert (hyde? d))
        (assert (hyde? (nth d 0)))
        (assert (hyde? (get-in d [1 :two])))
        (assert (hyde? (get-in d [2 :vec 2])))
        (assert (not (has-cache? d)))
        (let [
              ;[rm1 rv1] (DummyLib/zeroOutFirstArrayElement d)
              [rm2 rv2] (.zeroOutMapKeyOne js/DummyLib (nth d 2))
              [rm3 rv3] (.zeroOutMapKeyTen js/DummyLib (nth d 2))
              [rm4 rv4] (.zeroOutFirstArrayElement js/DummyLib (get-in d [2 :vec]))
              [rm5 rv5] (.zeroOutFirstArrayElement js/DummyLib (get-in d [2 :vec 2]))]

          ; cache information is only known locally
          (assert (not (has-cache? d)))
          (assert (has-cache? rm3))
          (assert (not (has-cache? (nth d 1))))

          ; (-> js/fdebug1 (set! (nth d 0)))
          ; (-> js/fdebug2 (set! (nth d 1)))
          ; (-> js/fdebug3 (set! (nth d 2)))
          ; (.log js/console (str (recurse-from-hyde-cache d)))

          (assert (=
            (recurse-from-hyde-cache d)
            [ {:bye 1, :now 2, :zero 3}
              {:two [], :three 3}
              {:one 0, :two 2, :vec [0 1 [0]] :ten 0} ]))

        ))))

(defn jsArraysEqual [a1 a2]
  (and (= (.-length a1) (.-length a2))
          (every? identity (map = a1 a2))))

(add-test "array functions"
    (fn []
      (let [v [3 6 9 12 15 18 21]
            a (apply array v)]

        (assert (jsArraysEqual (js/testFilterBiggerNine a) (array 12 15 18 21)))
        (assert (jsArraysEqual (js/testFilterBiggerNine v) (array 12 15 18 21)))
        )))

(add-test "extend_js"
    (fn []
      (let [jso (clj->js {:a 1
                          :b 2
                          :c {:x 10}})
            {:keys [a b]} jso]
        (assert (= (:b jso) 2))
        (assert (= (get-in jso [:c :x]) 10))
        (assoc! jso :b 100)
        (assert (= (:b jso) 100))
        (assoc-in! jso [:c :y] 14)
        (assert (= (get-in jso [:c :y]) 14))
        (update-in! jso [:c :x] inc)
        (assert (= (get-in jso [:c :x]) 11))
        (.log js/console (.stringify js/JSON jso))
      )
    )
)

; (add-test "js->clj with cycle?"
;     (fn []
;         (let [a (js-obj) b (js-obj)] (set! (.-a b) a) (set! (.-b a) b) (js->clj a)) ; #cljs surprise of the day
;         (let [c (js->clj js/p)])))
;      ))

  (run-all-tests (str "mrhyde: " s)))
