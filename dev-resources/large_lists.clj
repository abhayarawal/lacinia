(ns large-lists
  (:require
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]
    [criterium.core :as c]
    [com.walmartlabs.test-utils :refer [compile-schema]]
    [com.walmartlabs.lacinia.parser :as parser]
    [com.walmartlabs.lacinia :as lacinia]))

(s/def ::name string?)
(s/def ::age (s/int-in 1 100))
(s/def ::id integer?)
(s/def ::city string?)
(s/def ::item (s/keys :req-un [::name ::age ::id ::city]))

(def ^:private large-list
  (vec
    (repeatedly 5000
                #(gen/generate (s/gen ::item)))))


(def schema (compile-schema "large-lists-schema.edn"
                            {:resolve-list (constantly large-list)}))

(defn bench-mapv
  []
  (binding [c/*report-progress* true]
    (c/bench (mapv #(select-keys % [:name :age :id]) large-list))))

(defn bench-exec
  []
  (binding [c/*report-progress* true]
    (let [q "{ list { name age id }}"
          parsed (parser/parse-query schema q)]
      (c/bench
        (lacinia/execute-parsed-query parsed nil nil)))))

(defn bench-parse-and-execute
  []
  (binding [c/*report-progress* true]
    (c/bench
      (lacinia/execute schema "{ list { name age id }}" nil nil))))

(comment
  (bench-mapv)
  ;;  2.308310 ms
  ;; -- switch to bench --
  ;;  2.290889 ms

  (bench-exec)
  ;; 61.779221 ms -- base line
  ;; 59.036665 ms -- use hash-map instead of ordered-map
  ;; 66.580824 ms -- optimize (?!) combine-results (take 1)
  ;; 65.814538 ms -- optimize (?!) combine-results (take 2)
  ;; 67.019828 ms -- new base line (should match first base line, but eh, quick-bench)
  ;; 63.949198 ms -- optimize selector (removing some check steps)
  ;; 66.481703 ms -- remove executing timing penalty when not timing execution
  ;; 55.431582 ms -- optimize for single key/value pair (normal case outside of fragments)
  ;; -- switch to bench --
  ;; 58.143757 ms
  ;; 56.056103 ms -- use defrecord SelectionContext
  ;; 56.646881 ms -- less use of promises in ResolverResultPromise
  ;; 51.936539 ms -- tiny optimization on empty lists
  ;; 44.024393 ms -- leaf optimization


  (bench-parse-and-execute)
  ;; 67.814614 ms -- base line
  ;; -- switch to bench --
  )

