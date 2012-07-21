(ns calculation.core
  (:use okku.core)
  (:require common-actors.core))

(defn m-res [a b op r]
  {:type :result :op op :1 a :2 b :result r})

(def simple-calculator
  (actor
    (onReceive [{t :type o :op a :1 b :2}]
      (dispatch-on [t o]
        [:operation :+] (do (println (format "Calculating %s + %s" a b))
                          (! (m-res a b :+ (+ a b))))
        [:operation :-] (do (println (format "Calculating %s - %s" a b))
                          (! (m-res a b :- (- a b))))))))

(defn -main [& args]
  (let [system (actor-system "CalculatorApplication"
                             :port 2552)]
    (spawn simple-calculator :in system
           :name "simpleCalculator")))
