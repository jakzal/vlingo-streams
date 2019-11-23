package io.vlingo.pipes.actor;

import io.vlingo.actors.Actor;
import io.vlingo.common.Cancellable;
import io.vlingo.common.Completes;
import io.vlingo.common.Scheduled;
import io.vlingo.pipes.Source;

import java.util.Arrays;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;

public class MaterializedSourceActor extends Actor implements Scheduled<Void>, MaterializedSource {
    private final Source<Object> source;
    private final int pollingInterval;
    private final Queue<Object> queue;
    private final Cancellable cancellable;
    private final MaterializedSource selfAsMaterializedSource;

    public MaterializedSourceActor(Source<Object> source, int pollingInterval) {
        this.selfAsMaterializedSource = selfAs(MaterializedSource.class);
        this.source = source;
        this.pollingInterval = pollingInterval;
        this.queue = new PriorityQueue<>(32);

        this.cancellable =
                scheduler().schedule(selfAs(Scheduled.class), null, 0, pollingInterval);
    }

    @Override
    public void intervalSignal(Scheduled<Void> scheduled, Void aVoid) {
        source.poll().thenAccept(e -> queue.addAll(Arrays.asList(e)));
    }

    @Override
    public Completes<Optional<Object[]>> nextIfAny() {
        if (queue.isEmpty()) {
            return completes().with(Optional.empty());
        }

        int size = queue.size();
        Object[] res = new Object[size];
        for (var i = 0; i < size; i++) {
            res[i] = queue.poll();
        }

        return completes().with(Optional.of(res));
    }

    @Override
    public Completes<MaterializedSource> asSource() {
        return completes().with(selfAsMaterializedSource);
    }

    @Override
    public void stop() {
        this.cancellable.cancel();
        super.stop();
    }
}