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
    private int generation; // Geração da barreira - incrementa a cada reset

    public TeamBarrier(int teamSize, int timeout) {
        this.teamSize = teamSize;
        this.arrived = 0;
        this.broken = false;
        this.lock = new ReentrantLock();
        this.condition = lock.newCondition();
        this.timeout = timeout;
        this.startTime = System.currentTimeMillis();
        this.generation = 0;
    }

    public int await() throws InterruptedException {
        lock.lock();
        try {
            if (broken) {
                // Barreira foi quebrada
                return -1;
            }
            
            int savedGeneration = generation; // Capturar geração atual

            arrived++;
            int position = arrived;

            if (arrived == teamSize) {
                // Último jogador chegou - acordar todos
                condition.signalAll();
                return position;
            }

            // Esperar pelos outros jogadores
            while (arrived < teamSize && !broken && savedGeneration == generation) {
                long elapsed = System.currentTimeMillis() - startTime;
                long remaining = timeout - elapsed;

                if (remaining <= 0) {
                    broken = true;
                    condition.signalAll();
                    break;
                }

                // Usar await com timeout para evitar bloqueio indefinido
                try {
                    boolean timedOut = !condition.await(remaining, java.util.concurrent.TimeUnit.MILLISECONDS);
                    
                    // Verificar se uma nova geração começou (reset foi chamado)
                    if (savedGeneration != generation) {
                        // Nova pergunta começou - retornar indicando que barreira foi resetada
                        return -1;
                    }
                    
                    if (timedOut && arrived < teamSize) {
                        broken = true;
                        condition.signalAll();
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    broken = true;
                    condition.signalAll();
                    break;
                }
            }

            // Verificar novamente se geração mudou antes de retornar
            if (savedGeneration != generation) {
                return -1;
            }

            return position;

        } finally {
            lock.unlock();
        }
    }

    public void reset() {
        lock.lock();
        try {
            // Incrementar geração para invalidar threads antigas
            generation++;
            arrived = 0;
            broken = false;
            startTime = System.currentTimeMillis();
            // NÃO chamar signalAll aqui - threads antigas verificam geração e saem naturalmente
            // Threads novas (da nova pergunta) usarão a nova geração
        } finally {
            lock.unlock();
        }
    }
    
    public void breakBarrier() {
        lock.lock();
        try {
            broken = true;
            condition.signalAll(); // Acordar todas as threads bloqueadas
        } finally {
            lock.unlock();
        }
    }

    public boolean isBroken() {
        return broken;
    }
}