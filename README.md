# Akka Essentials with Scala | Rock the JVM

Course: https://github.com/rockthejvm/udemy-akka-essentials

## Akka Actors

### Actors, Messages and Behaviors

The initial setup for using actors is to create an ```ActorSystem```, define some ```Actors``` and instantiate them and sending and receiving messages

#### ActorSystem
ActorSystem is a heavyweight data structure that controls a number of threads under the hood which then allocates to running actors.
Only 1 ```ActorSystem``` per application instance. Name with alphanumeric characters.

```scala
val actorSystem = ActorSystem("firstActorSystem")
println(actorSystem.name)
```

#### Actors

Like humans sending and receiving messages.
* Uniquely identified
* Messages are asynchronous
* Each actor may respond differently
* Can't be 2 actors with same name under the same ```ActorSystem```
* Actors are **rereally** encapsulated:
  * Can't access internal data
  * Can't call methods
  * Can't instantiate by hand ```new ActorClass```
* Can only communicate with Actors via tell (!)
* Behavior of the actor is defeined by the function ```def receive: PartialFunction[Any, Unit]```. Can use the alias  ```Receive``` for it

**Define actor**

```scala
class WordCountActor extends Actor {
  // internal data
  var totalWords = 0

  // behavior
  def receive: Receive = {
    case message: String =>
      println(s"[word counter] I have received: $message")
      totalWords += message.split(" ").length
    case msg => println(s"[word counter] I cannot understand ${msg.toString}")
  }
}
```

**Instantiate Actors**

* Instantiate an actor with the ActorSystem
* It's a good idea to name actors
* Instantiating an Actor returns a reference to the actor ```ActorRef```

```scala
val wordCounter = actorSystem.actorOf(Props[WordCountActor], "wordCounter")
val anotherWordCounter = actorSystem.actorOf(Props[WordCountActor], "anotherWordCounter")
```

**Communicate Actors**

* Use the exclamation mark ! method
* Sending messages is async

```scala
wordCounter ! "I am learning Akka and it's pretty damn cool!"
anotherWordCounter ! "A different message"
```

**Extend Actor class**

The best practice is with an object companion

```scala
object Person {
  def props(name: String) = Props(new Person(name))
}

class Person(name: String) extends Actor {
  override def receive: Receive = {
    case "hi" => println(s"Hi, my name is $name")
    case _ =>
  }
}

val person = actorSystem.actorOf(Person.props("Bob"))
person ! "hi"
```