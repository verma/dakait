(ns dakait.test.util
  (:use clojure.test
        dakait.util))

(defmacro join-path-as-string [& args]
  `(.toString (join-path ~@args)))

(deftest util
  (testing "combines paths correctly"
    (is (= (join-path-as-string "/home" "test") "/home/test"))
    (is (= (join-path-as-string "/home" "/tmp") "/tmp"))
    (is (= (join-path-as-string "tmp") "tmp"))
    (is (= (join-path-as-string "." "/stuff") "/stuff"))
    (is (= (join-path-as-string "" "stuff") "./stuff"))
    (is (= (join-path-as-string "." "tmp") "./tmp")))

  (testing "map-vals works correctly"
    (is (= (map-vals inc {:a 1 :b 1}) {:a 2 :b 2}))
    (is (= (map-vals keyword {:hello "world" :bye "world"}) {:hello :world :bye :world}))))


