# Introduction

This tutorial mirrors the [Java remoting
tutorial](http://doc.akka.io/docs/akka/2.0.2/java/remoting.html) from the Akka
documentation. However, the code presented here will not be a direct port of
that tutorial.

The Akka remoting example from Java is available on github at the
following address:
```
https://github.com/akka/akka/tree/master/akka-samples/akka-sample-remote
```

The application we are going to code is a simple calculator server with two
clients. This is a bit contrived in order to show how Akka works (ad how to use
it from Okku).

The server's purpose is to accept messages asking for some simple mathematical
operations and to answer with results. At first, when the server is created, it
is composed of a single actor named "simpleCalculator" that knows how to do
additions and subtractions.

The first client looks-up the simpleCalculator actor by its address, and asks
it to compute some values. This is to illustrate how to interact with already
existing actors.

The second client wants to do multiplications and divisions. To do that, it
first has to create an actor on the server, which will be called
advancedCalculator and which will be able to handle multiplications and
divisions.

# Setup

To illustrate the distibuted nature of this example, we'll split it between
three separate projects. The default configuration you'll find in this repo
will work when the three programs run on the same machine, communicating
through network connections (each progam will have a different port). See the
end of this document for instructions on how to make it run on three separate
machines.

# Calculator server

## Setup

Before we can begin writing code, we have to create a new project and set it up
correctly. This is done by using ``lein new calculation`` and adding the
```clojure
[org.clojure.gaverhae/okku "0.1.0"]
```
to the ``:dependencies`` option and the
```clojure
:main calculation.core
```
options to the ``project.clj``.

If you read the corresponding Akka tutorial, they say you have to add a few
items to the ``application.conf`` file to enable remote actors. Since Clojure
already provides very good concurrency primitives, which makes the Actor model
relatively unattractive for a local Clojure application, Okku already takes
care of that (under the assumption that if you did not need distribution, you
would not be using Akka).

## simpleCalculator

Before we can define the simpleCalculator, we have to set up the namespace
properly:

```clojure
(ns calculation.core (use okku.core))
```

Next, we have to think about what kind of messages we're going to send. From
the calculation server, we are only going to send results. Following the "spec"
of the Akka remote example, the result message has to contain the operation
(which in the case of the Akka tutorial is encoded in the class of the
message), the operands and the result. We define a function that creates such
message as a map:

```clojure
(defn m-res [a b op r]
  {:type :result :op op :1 a :2 b :result r})
```

Lastly, before we can handle incoming messages, we have to be know what their
format is going to be. So let us work under the assumption that the messages
asking for a computation will be of the form:

```clojure
{:type :operation :op operator :1 operand1 :2 operand2}
```

We can thus write the simple-calculator actor as follows:

```clojure
(defactor simple-calculator []
  (onReceive [{t :type o :op a :1 b :2}]
    (dispatch-on [t o]
      [:operation :+] (do (println (format "Calculating %s + %s" a b))
                        (! (m-res a b :+ (+ a b))))
      [:operation :-] (do (println (format "Calculating %s - %s" a b))
                        (! (m-res a b :- (- a b)))))))
```

And finally, the main method, which simply creates the actor system and the
actor:

```clojure
(defn -main [& args]
  (let [system (actor-system "CalculatorApplication"
                             :port 2552)]
    (spawn simple-calculator [] :in system
           :name "simpleCalculator")))

```

So this creates an actor with address
```
akka://CalculatorApplication@127.0.0.1:2552/user/simpleCalculator
```

# Actor look-up

## Setup

Now that the application server is done, let's create a second project for the
look-up system. First, create the project as usual:
```
lein new lookup
```
then edit ``project.clj`` to add
```
[org.clojure.gaverhae/okku "0.1.0"]
```
to ``:dependencies`` and finally change the samespace declaration to
```clojure
(ns lookup.core
  (use okku.core))
```
Do not forget to also add the
```clojure
:main lookup.core
```
option to the ``defproject`` form.

## Testing the server

With this in place, we can already somewhat test the calculation server. If
you start the calculation server by running ``lein run``, and then open up a
repl in the lookup project with ``lein repl``, you should be able to type-in
the following commands:
```clojure
lookup.core=> (def as (actor-system "test" :port 2553))
lookup.core=> (def ra (look-up "akka://CalculatorApplication@127.0.0.1:2552/user/simpleCalculator" :in as))
lookup.core=> (.tell ra {:type :operation :op :+ :1 3 :2 5})
```
and you should see the correct output on the server.

## Look-up application

Again, we start by asking what kind of messages we want to send. We already
defined their format, so let's create the corresponding function:
```clojure
(defn m-op [op a b]
  {:type :operation :op op :1 a :2 b})
```
The next step is to create an actor that can send messages to the calculation
server and receive the answers back. To mirror the original Akka tutorial, this
actor also has to be able to receive a message asking it to compute something
(these last messages will be received from the main loop). These messages,
again mirroring the original tutorial, will be produced by the following
function:
```clojure
(defn m-tell [actor msg]
  {:type :proxy :target act :msg msg})
```

We can now design the look-up actor:
```clojure
(defactor printer []
  (onReceive [{t :type act :target m :msg op :op a :1 b :2 r :result}]
    (dispatch-on [t op]
      [:proxy nil] (! act m)
      [:result :+] (println (format "Add result: %s + %s = %s" a b r))
      [:result :-] (println (format "Sub result: %s - %s = %s" a b r)))))
```

Finally, we can create the main function:
```clojure
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
```
You should now be able to ``lein run`` the two projects and see them
communicate.


You should now be able to ``lein run`` the two projects and see them communicate.
