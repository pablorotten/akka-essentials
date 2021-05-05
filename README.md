# Akka Essentials with Scala | Rock the JVM

Course: https://github.com/rockthejvm/udemy-akka-essentials

## Akka Actors

* Actors are single-trhead. No locks needed
* Actor proccessing message is atomic. Once a thead starts doesn't release the message proccessing until is finished.
* Only 1 message delivery waranteed. No repeated messages
* Sender-receiver pair order is mantained

### ActorSystem
The initial setup for using actors is to create an ```ActorSystem```, define some ```Actors``` and instantiate them and sending and receiving messages.
ActorSystem is a heavyweight data structure that controls a number of threads under the hood which then allocates to running actors.
Only 1 ```ActorSystem``` per application instance. Name with alphanumeric characters.

```scala
val actorSystem = ActorSystem("firstActorSystem")
println(actorSystem.name)
```

### Actors

Like humans sending and receiving messages.
* Uniquely identified
* Messages are asynchronous
* Each actor may respond differently
* Can't be 2 actors with same name under the same ```ActorSystem```
* Actors are **really** encapsulated:
  * Can't access internal data
  * Can't call methods
  * Can't instantiate by hand ```new ActorClass```
* Can only communicate with Actors via tell (!)
* Behavior of the actor is defeined by the function ```def receive: PartialFunction[Any, Unit]```. Can use the alias  ```Receive``` for it

#### Define actor

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

#### Instantiate Actors

* Instantiate an actor with the ActorSystem
* It's a good idea to name actors
* Instantiating an Actor returns a reference to the actor ```ActorRef```

```scala
val wordCounter = actorSystem.actorOf(Props[WordCountActor], "wordCounter")
val anotherWordCounter = actorSystem.actorOf(Props[WordCountActor], "anotherWordCounter")
```

Extend Actor class with constructor

The best practice is with an object companion. In the object we instantiate the class with the parameters inside the
Props scope. We return it witht he factory method and we instantiate the Actor with that

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

#### Communicate Actors

* Use the exclamation mark ! method
* Sending messages is async
* Message must be **immutable** and **serializable**. Can use ```case``` classes and objects for it

```scala
wordCounter ! "I am learning Akka and it's pretty damn cool!"
anotherWordCounter ! "A different message"

case class SpecialMessage(contents: String)
simpleActor ! SpecialMessage("some special content")
```

**Send message to myself**

```context.self``` or just ```self``` is the self actor reference. Can use it to send a message to myself

```scala
class SimpleActor extends Actor {
  override def receive: Receive = {
    case message: String => println(s"[$self] I have received $message")
    case SendMessageToYourself(content) => self ! content
  }
}

case class SendMessageToYourself(content: String)

simpleActor ! SendMessageToYourself("I am an actor and I am proud of it")
```

1. We send a message to ```simpleActor``` using the case class ```SendMessageToYourself```
2. ```simpleActor``` receives the message with ```SendMessageToYourself``` and send the content String to himself
3. ```simpleActor``` receives the message with the String and prints it

**Reply messages**

```context.sender``` contains a reference to the actor who last sent the message. Whenever an actor uses ! passes himself as
a reference in ```context.sender```. Tell ```!``` method has sender as implicit parameter. By default is null, but when an actor
uses ```!``` passes ```self``` as implicit value.

```scala
class SimpleActor extends Actor {
  override def receive: Receive = {
    case "Hi!" => context.sender ! "Hello, there!" // replying to a message
    case message: String => println(s"[$self] I have received $message")
    case SayHiTo(ref) => ref ! "Hi!" // alice is being passed as the sender
  }
}

case class SayHiTo(ref: ActorRef)
alice ! SayHiTo(bob)
```

1. We send to **alice** the message with the case class ```SayHiTo``` that contains a ```ref``` to **bob**
2. **alice** receives the message, reads the ```ActirRef``` of ```SayHiTo``` that happens to be **bob** and send a message to him with the String "Hi!"
3. **bob** receives a message with the String "Hi!". Checks the ```context.sender``` which is **alice** and sends to her a message witht he String "Hello, there!"
4. **alice** receives a message with the String "Hello, there!" and prints her reference with the message

**Dead letter**

Dead letter is a fake actor in akka which receive messages that are not sent to anyone. Garbage pool of message.
If we send a message from the big context (not an actor) to an actor and he tries to answer us, the answer goes to **dead letter**

```scala
class SimpleActor extends Actor {
  override def receive: Receive = {
    case "Hi!" => context.sender ! "Hello, there!" // replying to a message
  }
}

alice ! "Hi!"
```

1. Send "Hi!" to **alice** from big context
2. **alice** tries to reply us, but sender is null. So the answer goes to **dead letter**
```[INFO] [akkaDeadLetter] [akka://actorCapabilitiesDemo/deadLetters] Message from Actor[akka://actorCapabilitiesDemo/user/alice#1027529614] to Actor[akka://actorCapabilitiesDemo/deadLetters] was not delivered. [1] dead letters encountered.```

**Forward messages**

We want an actor to send a message to another, and the receiver forward the same message to a 3rd one **but keeping the 1st actor as sender**: A -> B -> C
With ```forward``` we can achieve that. ```forward``` keeps as ```sender``` the original sender, no matter who is actually sending the message.
```scala
class SimpleActor extends Actor {
  override def receive: Receive = {
    case message: String => println(s"[$self] I have received $message")
    case WirelessPhoneMessage(content, ref) => ref forward (content + "s") // i keep the original sender of the WPM
  }
}

case class WirelessPhoneMessage(content: String, ref: ActorRef)
alice ! WirelessPhoneMessage("Hi", bob) // no sender (big context)
```

1. **alice** receives the message with ```WirelessPhoneMessage``` with a String and a reference to **bob**
2. **alice** forward the String adding a "s" to **bob**
3. **bob** receives a message with a String from **alice**. But checks the ```sender``` and is **dead letter**, the original sender

### Changing Actor Behavior

Dealing with states in Actors, the natural approach is create a ```var``` for it and in the message handler ```receive```
take in account that state with if/else or similars. But this is a terrible approach. We are using side effects and having
conditions inside the message handler can create a very complex code.

#### context.become

The solution is to create a message handler for each state and replace the the ```receive``` function as soon the state changes.

To achieve that we have ```context.become(handler)```. There can be only one ``receive``` message handler at the same time, but we can write others
and swap them as the current ```receive``` for that actor:

```scala
class StatelessActor extends Actor {
  override def receive: Receive = handlerA // set handlerA by default

  def handlerA: Receive = {
    case A =>
    case B => context.become(handlerB) // change my receive handler to handlerB
    case s: String => println(s"$s >> handled by handlerA")
  }

  def handlerB: Receive = {
    case A => context.become(handlerA)
    case B =>
    case s: String => println(s"$s >> handled by handlerB")
  }
}

val statelessActor = system.actorOf(Props[StatelessActor])

statelessActor ! B
statelessActor ! "hi"
```

1. statelessActor receives message with B, changes the defualt receive ```handlerA with ```handlerB```
2. statelessActor receives the String "hi!". Printlns "hi! >> handled by handlerB" since is using ```handlerB```

**Stacking handlers**

We can use the stack of handlers in order to acummulate handlers to be used in the following calls. We can push and pop handlers
from the stack whenever we want.

* ```context.become(handler, true)```/```context.become(handler)```: swaps the receive function with handler inmediatly
* ```context.become(handler, false)```: adds the handler to the stack
* ```context.unbecome()```: pop the first handler from the stack

Every time the actor's ```receive``` function is called, picks the top handler function of the stack

So for example, a kid can be happy if eats chocolate and sad if eats vegetables. By default is happy, but the more vegetable he eats, the saddest he become.
If we give him chocolate he becomes a bit happier, but if we want the kid to be totally happy we have to give him one chocolate for each vegetable he ate:

```scala
class StatelessFussyKid extends Actor {

  override def receive: Receive = happyReceive

  def happyReceive: Receive = {
    case Food(VEGETABLE) => context.become(sadReceive, false) // change my receive handler to sadReceive
    case Food(CHOCOLATE) =>
    case Ask(_) => sender() ! KidAccept
  }

  def sadReceive: Receive = {
    case Food(VEGETABLE) => context.become(sadReceive, false)
    case Food(CHOCOLATE) => context.unbecome()
    case Ask(_) => sender() ! KidReject
  }
}
```
Stack:[] receive: happyReceive (default)
1. Food(veg) >> Stack:[sadReceive] receive: sadReceive
2. Food(veg) >> Stack:[sadReceive, sadReceive] receive: sadReceive
3. Food(choco) >> Stack:[sadReceive] receive: sadReceive
4. Food(choco) >> Stack:[] receive: happyReceive (default)
