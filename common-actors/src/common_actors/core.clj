(ns common-actors.core
  (use okku.core))

(defn- m-res [a b op res]
  {:type :result :1 a :2 b :op op :result res})

(defactor advanced-calculator []
  (onReceive [{t :type o :op a :1 b :2}]
    (dispatch-on [t o]
      [:operation :*] (do (println (format "Calculating %s * %s" a b))
                        (! (m-res a b :* (* a b))))
      [:operation :d] (do (println (format "Calculating %s / %s" a b))
                        (! (m-res a b :d (/ a b)))))))
