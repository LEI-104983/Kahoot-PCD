package kahoot.coordination;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TeamBarrier {
    private final int teamSize;
    private int arrived;
    private boolean broken;
    private final Lock lock;
    private final Condition condition;
    private final int timeout;
    private long startTime;

    public TeamBarrier(int teamSize, int timeout) {
        this.teamSize = teamSize;
        this.arrived = 0;
        this.broken = false;
        this.lock = new ReentrantLock();
        this.condition = lock.newCondition();
        this.timeout = timeout;
        this.startTime = System.currentTimeMillis();
    }

    public int await() throws InterruptedException {
        lock.lock();
        try {
            if (broken) {
                return -1;
            }

            arrived++;
            int position = arrived;

            if (arrived == teamSize) {
                // Último jogador chegou - acordar todos
                condition.signalAll();
                return position;
            }

            // Esperar pelos outros jogadores
            while (arrived < teamSize && !broken) {
                long elapsed = System.currentTimeMillis() - startTime;
                long remaining = timeout - elapsed;

                if (remaining <= 0) {
                    broken = true;
                    condition.signalAll();
                    break;
                }

                condition.await();
            }

            return position;

        } finally {
            lock.unlock();
        }
    }

    public void reset() {
        lock.lock();
        try {
            arrived = 0;
            broken = false;
            startTime = System.currentTimeMillis();
        } finally {
            lock.unlock();
        }
    }

    public boolean isBroken() {
        return broken;
    }
}