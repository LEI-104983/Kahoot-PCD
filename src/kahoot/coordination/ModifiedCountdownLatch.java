package kahoot.coordination;

public class ModifiedCountdownLatch {
    private int count;
    private final int bonusFactor;
    private final int bonusCount;
    private int currentBonus;

    public ModifiedCountdownLatch(int bonusFactor, int bonusCount, int count) {
        this.bonusFactor = bonusFactor;
        this.bonusCount = bonusCount;
        this.count = count;
        this.currentBonus = 0;
    }

    public synchronized int countDown() {
        if (count <= 0) {
            return 1; // Sem bónus
        }

        count--;
        int bonus = 1; // Sem bónus por padrão
        if (currentBonus < bonusCount) {
            currentBonus++;
            bonus = bonusFactor; // Bónus para os primeiros
        }
        
        // Quando o contador chegar a zero, desbloquear todas as threads em await()
        if (count == 0) {
            notifyAll();
        }
        
        return bonus;
    }

    public synchronized void await() throws InterruptedException {
        while (count > 0) {
            wait();
        }
    }

    // Reset para nova pergunta
    public synchronized void reset(int newCount) {
        this.count = newCount;
        this.currentBonus = 0;
        notifyAll(); // Acordar threads que possam estar esperando
    }
}