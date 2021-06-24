package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2actors.ChildActors.CreditCard.{AttachToAccount, CheckStatus}

object ChildActors extends App {

  // Actors can create other actors

  object Parent {
    case class CreateChild(name: String)
    case class TellChild(message: String)
  }

  class Parent extends Actor {
    import Parent._

    override def receive: Receive = {
      case CreateChild(name) =>
        println(s"${self.path} create child")
        // create a new actor right HERE
        val childRef = context.actorOf(Props[Child], name)
        context.become(withChild(childRef))

    }

    def withChild(childRef: ActorRef): Receive = {
      case TellChild(message) =>
        if (childRef != null) // not needed
          childRef forward message
    }
  }

  class Child extends Actor {
    override def receive: Receive = {
      case message => println(s"${self.path} I got: $message")
    }
  }

  import Parent._

  val system = ActorSystem("ParentChildDemo")
  val parent  = system.actorOf(Props[Parent], "parent")
  parent ! CreateChild("child")
  parent ! TellChild("Hey Kid!")
  parent ! CreateChild("child")

  // actor hierarchies
  // parent -> child -> grandChild
  //        -> child2 ->

  /*
    Guardian actors (top-level)
    - /system = system guardian
    - /user = user-level guardian
    - / = the root guardian
   */

  /**
   * Actor selection
   */
  val childSelection = system.actorSelection("/user/parent/child") // goes to the Child actor Parent just created
  val childSelection = system.actorSelection("/user/parent/child2") // goes to dead letter
  childSelection ! "I found you!"

  /**
   * Danger!
   *
   * NEVER PASS MUTABLE ACTOR STATE, OR THE `THIS` REFERENCE, TO CHILD ACTORS.
   *
   * NEVER IN YOUR LIFE.
   */

  //  Wrong bank system example where a credit card child Actor of a bank account has access to the bank account instance instead of just the actor reference

  object NaiveBankAccount {
    case class Deposit(amount: Int)
    case class Withdraw(amount: Int)
    case object InitializeAccount
  }
  class NaiveBankAccount extends Actor {
    import NaiveBankAccount._
    import CreditCard._

    var amount = 0

    override def receive: Receive = {
      case InitializeAccount =>
        val creditCardRef = context.actorOf(Props[CreditCard], "card")
        creditCardRef ! AttachToAccount(this) // ⚠ Error 1: sending this to a child ⚠ NEVER do that, send a reference not the instance.
      case Deposit(funds) => deposit(funds)
      case Withdraw(funds) => withdraw(funds)

    }

    def deposit(funds: Int) = {
      println(s"${self.path} depositing $funds on top of $amount")
      amount += funds
    }
    def withdraw(funds: Int) = {
      println(s"${self.path} withdrawing $funds from $amount")
      amount -= funds
    }
  }

  object CreditCard {
    case class AttachToAccount(bankAccount: NaiveBankAccount) // ⚠ Error 2: the credit card will have full access to the BankAccount methods and variables !! TERRIBLE!!!
    // case class AttachToAccount(bankAccount: NaiveBankAccount) // ✔ that it's ok. It's just a reference, no access to variables/methods, just send messages
    case object CheckStatus
  }

  class CreditCard extends Actor {
    override def receive: Receive = {
      case AttachToAccount(account) => context.become(attachedTo(account))
    }

    def attachedTo(account: NaiveBankAccount): Receive = {
      case CheckStatus =>
        println(s"${self.path} your message has been processed.")
        // benign
        account.withdraw(1) // // ⚠ Error 3: A child actor using parent actor methods. this is wrong!!! just send messages
    }
  }

  import NaiveBankAccount._
  import CreditCard._

  val bankAccountRef = system.actorOf(Props[NaiveBankAccount], "account")
  bankAccountRef ! InitializeAccount // creates a child actor CreditCard that has a reference to NaiveBankAccount itself
  bankAccountRef ! Deposit(100)

  Thread.sleep(500)
  val creditCardSelection = system.actorSelection("/user/account/card") // reference a credit card using actorSelection
  creditCardSelection ! CheckStatus // CreditCard CheckStatus message handler can change the status of a NaiveBankAccount variable because has access to it!!

  // WRONG!!!!!!
}
