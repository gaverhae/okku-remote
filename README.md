For Okku 0.1.3.

# Introduction

This tutorial mirrors the [Java remoting
tutorial](http://doc.akka.io/docs/akka/2.0.2/java/remoting.html) from the Akka
documentation.

The application we are going to code is a calculator server with two clients.
This is a bit contrived in order to show how Akka works (and how to use it from
Okku).

The purpose of the server is to accept messages asking for some mathematical
operations and to answer with results. At first, when the server is created, it
is composed of a single actor named "simpleCalculator" that knows how to do
additions and subtractions.

The first client looks-up the simpleCalculator actor by its address, and asks
it to compute some values. This is to illustrate how to interact with
previously existing remote actors.

The second client wants to do multiplications and divisions. To do that, it
first has to create an actor on the server, which will be called
``advancedCalculator`` and which will be able to handle multiplications and
divisions.

# Setup

To illustrate the distibuted nature of this example, we'll split it between
three separate projects. The default (as in "in-code") configuration you'll
find in this repo will work when the three programs run on the same machine,
communicating through network connections (each progam will have a different
port). See the end of this document for instructions on how to make it run on
three separate machines.

We shall actually create a fourth project to serve as a library for the other
three, to hold the common code. This is not only good practice to reduce
duplication, it is actually required in this case: when the second client wants
to ask the server to create an actor, they have to share the exact same
definition of the new to-be-created actor, and that can only be achieved by
sharing the same ``class`` files to describe it (which here will be contained
in a ``jar`` file).

# Calculator server

## Setup

Before we can begin writing code, we have to create a new project and set it up
correctly. This is done by using ``lein new calculation`` and adding the
```clojure
[org.clojure.gaverhae/okku "0.1.3"]
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

Before we can define the ``simpleCalculator``, we have to set up the namespace
properly:

```clojure
(ns calculation.core
  (:use okku.core))
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

Lastly, before we can handle incoming messages, we have to know what their
format is going to be. So let us work under the assumption that the messages
asking for a computation will be of the form:

```clojure
{:type :operation :op operator :1 operand1 :2 operand2}
```

We can thus write the simple-calculator actor as follows:

```clojure
(def simple-calculator
  (actor
    (onReceive [{t :type o :op a :1 b :2}]
      (dispatch-on [t o]
        [:operation :+] (do (println (format "Calculating %s + %s" a b))
                          (! (m-res a b :+ (+ a b))))
        [:operation :-] (do (println (format "Calculating %s - %s" a b))
                          (! (m-res a b :- (- a b))))))))
```

And finally, the main method, which creates the actor system and the actor:

```clojure
(defn -main [& args]
  (let [system (actor-system "CalculatorApplication"
                             :port 2552)]
    (spawn simple-calculator :in system
           :name "simpleCalculator")))

```

So this creates an actor with address
```
akka://CalculatorApplication@127.0.0.1:2552/user/simpleCalculator
```
(unless this is overridden by the configuration file).

# Actor look-up

## Setup

Now that the application server is done, let's create a second project for the
look-up system. First, create the project as usual:
```
lein new lookup
```
then edit ``project.clj`` to add
```
[org.clojure.gaverhae/okku "0.1.3"]
```
to ``:dependencies`` and finally change the namespace declaration to
```clojure
(ns lookup.core
  (:use okku.core))
```
Do not forget to also add the
```clojure
:main lookup.core
```
option to the ``defproject`` form.

## Testing the server

With this in place, we can already somewhat test the calculation server. If you
start the calculation server by running ``lein run``, and then open up a repl
in the ``lookup`` project with ``lein repl``, you should be able to type the
following commands:
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
(def printer
  (actor
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
        la (spawn printer :in as)
        ra (look-up "akka://CalculatorApplication@127.0.0.1:2552/user/simpleCalculator"
                    :in as :name "looked-up")]
    (while true
      (.tell la (m-tell ra (m-op (if (zero? (rem (rand-int 100) 2)) :+ :-)
                                 (rand-int 100) (rand-int 100))))
      (try (Thread/sleep 2000)
        (catch InterruptedException e)))))
```
You should now be able to ``lein run`` the two projects and see them
communicate. (You do have to run the server first.)

# Creation application

## Setup

The basic setup is yet again the same: create a new project, add a default main
class, add the dependency, and use it in the main class. Say this project is
named creation.

We actually need a bit more of a setup. In order to be able to remotely create
an actor, both the system that asks for the actor and the system that hosts it
must have access to the corresponding class. To support remote actor creation,
we are thus going to create a fourth project, which will be a library of the
common actors between the server and the creation client.

## Common actors

So we create this new project, say "common-actors", we add to it a dependency
on okku.core, we use okku.core in the core.clj file, and we this time do not
add any ``:main`` directive to the ``project.clj`` as this is going to be
strictly a library.

In the common-actors library, we add the advanced-calculator actor, which is
nearly identical to the simple-calculator seen earlier:
```clojure
(defn m-res [a b op r]
  {:type :result :op op :1 a :2 b :result r})

(def advanced-calculator
  (actor
    (onReceive [{t :type o :op a :1 b :2}]
      (dispatch-on [t o]
        [:operation :*] (do (println (format "Calculating %s * %s" a b))
                          (! (m-res a b :* (* a b))))
        [:operation :d] (do (println (format "Calculating %s / %s" a b))
                          (! (m-res a b :d (/ a b)))))))
```
Of course, we also had to copy the ``m-res`` function for this to work.

And that is all we need in this shared library. However, do not forget to add
this common library as a dependency to both the calculation and creation
applications (and to actually ``require`` it in the ``calculation.core``
namespace).

We need the generated proxy classes to be the same at the client (the actor
system that requires the creation of the actor) and at the server (the actor
system in which the actor is actually created). Since Clojure assigns random
names to classes generated at runtime, this can be problematic.  Therefore, the
``common-actors.core`` namespace needs to be aot-compiled (in practice, this
means adding the ``:aot common-actors.core`` line to the ``project.clj`` file).

However, this should not be too much of a problem, as in a real application,
there would probably be much more shared code, practically ensuring that the
shared library will be loaded on all sides. (For example, here, it would
probably be a good idea to declare the ``m-res`` function only in the
``common-actors`` package and then ``use`` it from ``creation`` and ``lookup``.
Similarly, the very high degree of similarity between simple-calculator and
advanced-calculator cries out for a refactoring, but that is beside the point
of this tutorial.)

As an aside, to use a local library from Leiningen, you need to run ``lein
install`` from the library to install it in your local maven repository.

## Creation application

The creation application will be very similar to the lookup application. First,
we define a local actor that will transmit messages, exactly as we did in the
lookup application.
```clojure
(defn m-op [op a b]
  {:type :operation :op op :1 a :2 b})
(defn m-tell [actor msg]
  {:type :proxy :target act :msg msg})

(def printer
  (actor
    (onReceive [{t :type act :target m :msg op :op a :1 b :2 r :result}]
      (dispatch-on [t op]
        [:proxy nil] (! act m)
        [:result :*] (println (format "Mul result: %s * %s = %s" a b r))
        [:result :d] (println (format "Div result: %s / %s = %s" a b r)))))
```

The interesting differences will of course be in the ``main`` function, though
it remains strikingly similar:
```clojure
(defn -main [& args]
  (let [as (actor-system "CreationApplication" :port 2554)
        la (spawn printer :in as)
        ra (spawn advanced-calculator :in as
                  :name "created"
                  :deploy-on "akka://CalculatorApplication@127.0.0.1:2552")]
    (while true
      (.tell la (m-tell ra (if (zero? (rem (rand-int 100) 2))
                             (m-op :* (rand-int 100) (rand-int 100))
                             (m-op :d (rand-int 10000) (rand-int 99)))))
      (try (Thread/sleep 2000)
    (catch InterruptedException e)))))
```

You should now be able to start all three projects with ``lein run`` and watch
it all work.

# Configuration files

Since we have named all of our remote actors, it is easy to change the options
through the configuration files. Refer to the Akka documentation for more
information on all the configurable options. Here, we shall only give a small
example of how configuration keys relate to actors in the code.

Say you want to change the three actor systems to use the public IP address of
your computer instead of 127.0.0.1, rendering them accessible from the outside
world. In each of the programs, you have to change the address of the local
actor system and the address of the remote one. This is done through the
``resources/application.conf`` file in each program. For example:
``calculation``
```config
akka.remote.netty.hostname = "192.168.1.101"
akka.remote.netty.port = 2652
```
``creation``
```config
akka.remote.netty {
  hostname = "192.168.1.101"
  port = 2653
}
akka.actor.deployment./created.remote = "akka://CalculatorApplication@10.2.32.46:2652"
```
``look-up``
```config
akka {
  remote {
    netty {
      hostname = "192.168.1.101"
    }
  }
}
akka.remote.netty.port = 2654
okku.lookup./lokked-up.hostname = "192.168.1.101"
okku.lookup./lokked-up.port = 2652
```

Where the different syntaxes are used just to show them off. Again, these are
plain Akka configuration files; see the Akka documentation for more
information.

If you have cloned the git repository for this tutorial, these configuration
options are already in the ``resources/`` folders, you only have to uncomment
all lines.

# Distributed setup

From the point of view of the three programs, they already are distributed.
Apart from the physical distribution of the ``jar`` files, there are no further
concerns.
