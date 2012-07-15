(ns lookup.core
  (use okku.core))

(defn m-op [op a b]
  {:type :operation :op op :1 a :2 b})
(defn m-tell [actor msg]
  {:type :proxy :target actor :msg msg})

(defactor printer []
  (onReceive [{t :type act :target m :msg op :op a :1 b :2 r :result}]
    (dispatch-on [t op]
      [:proxy nil] (! act m)
      [:result :+] (println (format "Add result: %s + %s = %s" a b r))
      [:result :-] (println (format "Sub result: %s - %s = %s" a b r)))))

(defn -main [& args]
  (let [as (actor-system "LookupApplication" :port 2553)
        la (spawn printer [] :in as)
        ra (look-up "akka://CalculatorApplication@127.0.0.1:2552/user/simpleCalculator"
                    :in as :name "looked-up")]
    (while true
      (.tell la (m-tell ra (m-op (if (zero? (rem (rand-int 100) 2)) :+ :-)
                                 (rand-int 100) (rand-int 100))))
      (try (Thread/sleep 2000)
        (catch InterruptedException e)))))
