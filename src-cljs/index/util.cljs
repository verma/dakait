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

(defn format-date
  "Given a time stamp in seconds since epoch, returns a nicely formated time"
  [n]
  (let [dt (* n 1000)
        now (.getTime (js/Date.))
        diffInSecs (quot (- now dt) 1000)
        diffInHours (quot diffInSecs 3600)]
    (cond
      (< diffInHours 1) "Less than an hour ago"
      (< diffInHours 2) "An hour ago"
      (< diffInHours 24) (str diffInHours " hours ago")
      (< diffInHours 48) "A day ago"
      (< diffInHours 168) (str (quot diffInHours 24) " days ago")
      :else (.toDateString (js/Date. dt)))))
