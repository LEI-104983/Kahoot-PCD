package kahoot.coordination;

public class ModifiedCountdownLatch {
    private int count;
    private final int bonusFactor;
    private final int bonusCount;
    private final int waitPeriod;
    private int currentBonus;
    private boolean timedOut;
    private long startTime;

    public ModifiedCountdownLatch(int bonusFactor, int bonusCount, int waitPeriod, int count) {
        this.bonusFactor = bonusFactor;
        this.bonusCount = bonusCount;
        this.waitPeriod = waitPeriod;
        this.count = count;
        this.currentBonus = 0;
        this.timedOut = false;
        this.startTime = System.currentTimeMillis();
    }

    public synchronized int countDown() {
        if (count <= 0 || timedOut) {
            return 1; // Sem bónus
        }

        count--;
        if (currentBonus < bonusCount) {
            currentBonus++;
            return bonusFactor; // Bónus para os primeiros
        }
        return 1; // Sem bónus
    }

    public synchronized void await() throws InterruptedException {
        while (count > 0 && !timedOut) {
            long elapsed = System.currentTimeMillis() - startTime;
            long remaining = waitPeriod - elapsed;

            if (remaining <= 0) {
                timedOut = true;
                break;
            }
            wait(remaining);
        }

        // Quando terminar, acordar todas as threads
        notifyAll();
    }

    public synchronized boolean isTimedOut() {
        return timedOut;
    }

    // Reset para nova pergunta
    public synchronized void reset(int newCount) {
        this.count = newCount;
        this.currentBonus = 0;
        this.timedOut = false;
        this.startTime = System.currentTimeMillis();
        notifyAll(); // Acordar threads que possam estar esperando
    }
}