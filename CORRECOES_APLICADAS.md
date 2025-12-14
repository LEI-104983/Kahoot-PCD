# Correções Aplicadas ao Projeto Kahoot-PCD

## ✅ Problemas Corrigidos

### 1. 🔴 **BUG CRÍTICO: Armazenamento de Respostas de Equipa** (RESOLVIDO)

**Problema:** As respostas eram armazenadas usando apenas `teamId`, mas o código tentava buscar por `teamId + "_" + username`.

**Solução:**
- Alterado `GameHandler.processAnswer()` para sempre armazenar respostas usando `playerKey = teamId + "_" + username`
- Agora `calculateTeamScore()` consegue encontrar as respostas individuais de cada jogador
- **Arquivo:** `src/kahoot/server/GameHandler.java` (linha 183)

---

### 2. ✅ **Race Condition em `endQuestion()`** (RESOLVIDO)

**Problema:** Múltiplas threads podiam chamar `endQuestion()` simultaneamente.

**Solução:**
- Adicionado `AtomicBoolean` para cada pergunta (`questionEnded`)
- Uso de `compareAndSet(false, true)` para garantir execução única
- **Arquivo:** `src/kahoot/server/GameHandler.java` (linhas 14, 95, 195, 250)

---

### 3. ✅ **TeamBarrier - Timeout Incorreto** (RESOLVIDO)

**Problema:** `await()` usava `condition.await()` sem timeout, podendo bloquear indefinidamente.

**Solução:**
- Implementado `await(timeout, TimeUnit)` com timeout calculado
- Tratamento adequado de `InterruptedException`
- **Arquivo:** `src/kahoot/coordination/TeamBarrier.java` (linhas 43-60)

---

### 4. ✅ **ModifiedCountdownLatch - Reset Entre Perguntas** (RESOLVIDO)

**Problema:** O latch não era resetado entre perguntas, causando problemas com múltiplas perguntas.

**Solução:**
- Adicionado método `reset(int newCount)` ao `ModifiedCountdownLatch`
- Reset automático quando nova pergunta individual começa
- **Arquivo:** `src/kahoot/coordination/ModifiedCountdownLatch.java` (linhas 55-62)
- **Arquivo:** `src/kahoot/server/GameHandler.java` (linhas 89-95)

---

### 5. ✅ **Prevenção de Respostas Duplicadas** (RESOLVIDO)

**Problema:** Jogadores podiam enviar múltiplas respostas para a mesma pergunta.

**Solução:**
- Adicionado `Set<String> answeredPlayers` para rastrear jogadores que já responderam
- Verificação antes de processar resposta
- **Arquivo:** `src/kahoot/server/GameHandler.java` (linhas 14, 95, 170-175)

---

### 6. ✅ **Timeout de Conexão** (RESOLVIDO)

**Problema:** `readObject()` bloqueava indefinidamente, deixando threads presas.

**Solução:**
- Configurado `socket.setSoTimeout(60000)` no construtor
- Tratamento de `SocketTimeoutException` no loop de leitura
- **Arquivo:** `src/kahoot/server/DealWithClient.java` (linhas 21-26, 43-47)

---

### 7. ✅ **Player.addScore() Thread-Safe** (RESOLVIDO)

**Problema:** `addScore()` não era thread-safe, podendo causar race conditions.

**Solução:**
- Método marcado como `synchronized`
- Campo `score` marcado como `volatile` para visibilidade
- **Arquivo:** `src/kahoot/game/Player.java` (linhas 8, 19)

---

### 8. ✅ **Tratamento de Desconexões** (RESOLVIDO)

**Problema:** Desconexões durante o jogo não eram notificadas ao `GameHandler`.

**Solução:**
- Adicionado método `removePlayer()` no `GameHandler`
- Notificação automática em `DealWithClient.disconnect()`
- **Arquivo:** `src/kahoot/server/GameHandler.java` (linhas 58-66)
- **Arquivo:** `src/kahoot/server/DealWithClient.java` (linhas 95-104)

---

### 9. ✅ **Melhorias Adicionais**

- **Timer:** Cancelamento adequado do timer ao terminar pergunta
- **Sincronização:** Melhor uso de `synchronized` e estruturas thread-safe
- **Comentários:** Corrigido comentário sobre índices de perguntas

---

## 📋 Resumo das Alterações por Arquivo

### `src/kahoot/server/GameHandler.java`
- ✅ Corrigido armazenamento de respostas (sempre por `playerKey`)
- ✅ Adicionado `answeredPlayers` e `questionEnded` para controle
- ✅ Prevenção de respostas duplicadas
- ✅ Sincronização com `AtomicBoolean` para `endQuestion()`
- ✅ Reset de `ModifiedCountdownLatch` entre perguntas
- ✅ Método `removePlayer()` para desconexões

### `src/kahoot/coordination/ModifiedCountdownLatch.java`
- ✅ Adicionado método `reset(int newCount)`

### `src/kahoot/coordination/TeamBarrier.java`
- ✅ Timeout adequado no `await()` usando `await(timeout, TimeUnit)`

### `src/kahoot/game/Player.java`
- ✅ `addScore()` agora é `synchronized`
- ✅ Campo `score` marcado como `volatile`

### `src/kahoot/server/DealWithClient.java`
- ✅ Timeout de conexão configurado (60 segundos)
- ✅ Tratamento de `SocketTimeoutException`
- ✅ Notificação de desconexão ao `GameHandler`

---

## 🎯 Resultado

Todos os problemas críticos de **concorrência** e **distribuição** foram corrigidos:

- ✅ **Concorrência:** Race conditions eliminadas, sincronização adequada
- ✅ **Distribuição:** Timeouts implementados, desconexões tratadas
- ✅ **Funcionalidade:** Bug crítico das respostas de equipa corrigido
- ✅ **Robustez:** Prevenção de duplicados, melhor gestão de recursos

O projeto agora está **thread-safe** e **robusto** para uso em ambiente distribuído! 🚀

