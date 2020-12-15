package akkaCakes;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import akka.actor.AbstractActor;
import akka.pattern.Patterns;

public abstract class Producer <T> extends AbstractActor {
    private final Queue<T> products = new LinkedList<>();
    private final int maxSize = 15;
    private boolean running;
    public abstract CompletableFuture<T> make() throws InterruptedException;
    public abstract Class<T> getClassObj();
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(getClassObj(), products::add)
                .match(MakeOne.class, () -> running, r -> {
                    if (products.size() > maxSize) {
                        running = false;
                    } else {
                        CompletableFuture<T> future = make();
                        Patterns.pipe(future, context().dispatcher()).to(getSelf());
                        future.thenAcceptAsync((T unused) -> getSelf().tell(new MakeOne(), getSelf()));
                    }
                }).match(GiveOne.class, r -> {
                    if (!products.isEmpty()) {
                        sender().tell(products.remove(), getSelf());
                    } else {
                        Patterns.pipe(make(), context().dispatcher()).to(sender());
                    }
                    if(!running) {
                        running = true;
                        self().tell(new MakeOne(), getSelf());
                    }
                })
                .build();}
}
