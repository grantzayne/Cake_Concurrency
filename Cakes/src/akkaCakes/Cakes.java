package akkaCakes;

import akka.actor.*;
import akka.pattern.Patterns;
import akkaUtils.AkkaConfig;
import dataCakes.Cake;
import dataCakes.Gift;
import dataCakes.Sugar;
import dataCakes.Wheat;

import java.io.Serializable;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
@SuppressWarnings("serial")
class GiftRequest implements Serializable{}
@SuppressWarnings("serial")
final class MakeOne implements Serializable{}
@SuppressWarnings("serial")
final class GiveOne implements Serializable{}
//--------

class Alice extends Producer<Wheat>{

	@Override
	public CompletableFuture<Wheat> make() {
		// TODO Auto-generated method stub
		return CompletableFuture.supplyAsync(Wheat::new);
	}

	@Override
	public Class<Wheat> getClassObj() {
		// TODO Auto-generated method stub
		return Wheat.class;
	}
	
}
class Bob extends Producer<Sugar>{

	@Override
	public CompletableFuture<Sugar> make() {
		// TODO Auto-generated method stub
		return CompletableFuture.supplyAsync(Sugar::new);
	}

	@Override
	public Class<Sugar> getClassObj() {
		// TODO Auto-generated method stub
		return Sugar.class;
	}
	
}
class Charles extends Producer<Cake>{
	private ActorRef alice,bob;
	Charles(ActorRef alice,ActorRef bob){
		this.alice = alice;
		this.bob = bob;
	}
	@Override
	public CompletableFuture<Cake> make() {
		// TODO Auto-generated method stub
        CompletableFuture<Object> futureW = Patterns.ask(alice, new GiveOne(), Duration.ofMillis(10000)).toCompletableFuture();
        CompletableFuture<Object> futureS = Patterns.ask(bob, new GiveOne(), Duration.ofMillis(10000)).toCompletableFuture();
        return CompletableFuture.allOf(futureW, futureS).thenApplyAsync(v -> {
            Wheat wheat = (Wheat) futureW.join();
            Sugar sugar = (Sugar) futureS.join();
            return new Cake(sugar, wheat);
        });
	}

	@Override
	public Class<Cake> getClassObj() {
		// TODO Auto-generated method stub
		return Cake.class;
	}

}
class Tim extends AbstractActor{
  private int hunger;
  private ActorRef charles;
  Tim(int hunger, ActorRef charles) {this.charles = charles; this.hunger=hunger;}
  private boolean running=true;
  private ActorRef originalSender=null;
  public Receive createReceive() {
    return receiveBuilder()
      .match(GiftRequest.class,()->originalSender==null,gr->{originalSender=sender(); charles.tell(new GiveOne(), self());})
      .match(Cake.class,()->running,c->{
        hunger-=1;
        System.out.println("YUMMY but I'm still hungry "+hunger);
        if(hunger>0) {charles.tell(new GiveOne(), self());return;}
        running=false;
        originalSender.tell(new Gift(),self());
        })
      .build();}}

public class Cakes{
  public static void main(String[] args){
    ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);
    Gift g=computeGift(1000);
    assert g!=null;
    System.out.println(
      "\n\n-----------------------------\n\n"+
      g+
      "\n\n-----------------------------\n\n");
    }
  public static Gift computeGift(int hunger){
    ActorSystem s=AkkaConfig.newSystem("Cakes", 2501,Collections.emptyMap()/*AkkaConfig.makeMap(
        "Tim","192.168.56.1",
        "Bob","192.168.56.1",
        "Charles","192.168.56.1"
        //Alice stays local
        )*/);
    ActorRef alice=//makes wheat
      s.actorOf(Props.create(Alice.class, () -> new Alice()),"Alice");
    ActorRef bob=//makes sugar
      s.actorOf(Props.create(Bob.class, () -> new Bob()),"Bob");
    ActorRef charles=// makes cakes with wheat and sugar
      s.actorOf(Props.create(Charles.class,()->new Charles(alice,bob)),"Charles");
    ActorRef tim=//tim wants to eat cakes
      s.actorOf(Props.create(Tim.class,()->new Tim(hunger,charles)),"Tim");
    alice.tell(charles,tim);
    bob.tell(charles,tim);
    CompletableFuture<Object> gift = Patterns.ask(tim,new GiftRequest(), Duration.ofMillis(10_000_000)).toCompletableFuture();
    try{return (Gift)gift.join();}
    finally{
      alice.tell(PoisonPill.getInstance(),ActorRef.noSender());
      bob.tell(PoisonPill.getInstance(),ActorRef.noSender());
      charles.tell(PoisonPill.getInstance(),ActorRef.noSender());
      tim.tell(PoisonPill.getInstance(),ActorRef.noSender());
      s.terminate();
      }
    }
  }