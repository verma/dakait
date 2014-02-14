(ns dakait.test.tags
  (:use clojure.test
        dakait.tags))

(def not-nil? (complement nil?))

(deftest tags
  (testing "default state"
    (is (= (get-all-tags) {})))

  (testing "Add tags"
    (add-tag "Test name" "/some/place" "#eee")
    (is (= (count (get-all-tags)) 1))
    (let [t (find-tag "Test name")]
      (is (not-nil? t))
      (is (= (:target t) "/some/place"))
      (is (= (:color t) "#eee"))))

  (testing "Remove tags"
    (add-tag "Test name" "/some/place" "#eee")
    (remove-tag "Test name")
    (is (zero? (count (get-all-tags)))))

  (testing "Remove correct tag"
    (add-tag "Test name" "/some/place" "#eee")
    (add-tag "Test name2" "/some/place" "#eee")
    (remove-tag "Test name")
    (is (= (count (get-all-tags)) 1))
    (is (not-nil? (find-tag "Test name2")))))
