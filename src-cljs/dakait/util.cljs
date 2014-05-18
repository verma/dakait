(ns dakait.util
  )

(defn format-file-size
  "Make a formatted string out of the given number of bytes"
  [n]
  (let [[size postfix] (cond
                         (< n 1000) [n "B"]
                         (< n 1000000) [(/ n 1000) "K"]
                         (< n 1000000000) [(/ n 1000000.0) "M"]
                         (< n 1000000000000) [(/ n 1000000000.0) "G"]
                         (< n 1000000000000000) [(/ n 1000000000000.0) "T"]
                         :else [n "B"])
        fixedSize (if (< n 1000000) 0 1)]
    (apply str [(.toFixed size fixedSize) postfix])))

(defn- sub-hour-format-date
  [n]
  (let [now (quot (.getTime (js/Date.)) 1000)
        diffInSecs (- now n)]
    (cond
      (< diffInSecs 5) "Less than 5 seconds ago"
      (< diffInSecs 10) "Less than 10 seconds ago"
      (< diffInSecs 60) "Less than a minute ago"
      :else (str (inc (quot diffInSecs 60)) " minutes ago"))))

(defn format-date
  "Given a time stamp in seconds since epoch, returns a nicely formated time"
  [n]
  (let [dt (* n 1000)
        now (.getTime (js/Date.))
        diffInSecs (quot (- now dt) 1000)
        diffInHours (quot diffInSecs 3600)]
    (cond
      (< diffInHours 1) (sub-hour-format-date n)
      (< diffInHours 2) "An hour ago"
      (< diffInHours 24) (str diffInHours " hours ago")
      (< diffInHours 48) "A day ago"
      (< diffInHours 168) (str (quot diffInHours 24) " days ago")
      :else (.toDateString (js/Date. dt)))))

(defn duration-since
  "Given a time stamp (time in seconds since epoch), returns how much time in seconds has passed since"
  [n]
  (let [now (quot (.getTime (js/Date.)) 1000)]
    (- now n)))
