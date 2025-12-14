# Kahoot-PCD: Sistema de Quiz Multiplayer

## 📋 Índice

1. [Visão Geral](#visão-geral)
2. [Arquitetura do Sistema](#arquitetura-do-sistema)
3. [Componentes Principais](#componentes-principais)
4. [Fluxo Completo do Jogo](#fluxo-completo-do-jogo)
5. [Partes Distribuídas](#partes-distribuídas)
6. [Partes Concorrentes](#partes-concorrentes)
7. [Mecanismos de Coordenação](#mecanismos-de-coordenação)
8. [Sincronização e Thread-Safety](#sincronização-e-thread-safety)
9. [Como Executar](#como-executar)

---

## 🎯 Visão Geral

**Kahoot-PCD** é uma implementação de um sistema de quiz multiplayer inspirado no Kahoot, desenvolvido em Java para demonstrar conceitos de **Programação Concorrente e Distribuída (PCD)**.

### Características Principais

- ✅ **Arquitetura Cliente-Servidor**: Comunicação via sockets TCP
- ✅ **Múltiplos Jogos Simultâneos**: Servidor gerencia vários jogos independentes
- ✅ **Modo Individual e Equipa**: Alternância entre perguntas individuais e de equipa
- ✅ **Sistema de Pontuação**: Bónus por velocidade em perguntas individuais
- ✅ **Interface Gráfica**: Cliente com GUI Swing
- ✅ **Thread-Safe**: Sincronização adequada para ambiente concorrente

---

## 🏗️ Arquitetura do Sistema

```
┌─────────────────────────────────────────────────────────────┐
│                    GAME SERVER (Porta 8080)                  │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  GameServer                                            │  │
│  │  - Aceita conexões                                    │  │
│  │  - Gerencia múltiplos GameHandlers                    │  │
│  │  - Thread Pool (10 threads)                          │  │
│  └────────────────────────────────────────────────────────┘  │
│                          │                                    │
│        ┌──────────────────┼──────────────────┐               │
│        │                  │                  │               │
│  ┌─────▼─────┐    ┌──────▼──────┐   ┌──────▼──────┐       │
│  │ Game 1    │    │ Game 2       │   │ Game 3       │       │
│  │ (game1)   │    │ (game2)      │   │ (game3)      │       │
│  │           │    │              │   │              │       │
│  │ GameHandler│   │ GameHandler  │   │ GameHandler  │       │
│  │ - Quiz    │    │ - Quiz       │   │ - Quiz       │       │
│  │ - Teams   │    │ - Teams      │   │ - Teams      │       │
│  │ - Timer   │    │ - Timer      │   │ - Timer      │       │
│  └───────────┘    └──────────────┘   └──────────────┘       │
└─────────────────────────────────────────────────────────────┘
         │                    │                    │
         │                    │                    │
    ┌────▼────┐          ┌────▼────┐         ┌────▼────┐
    │ Cliente │          │ Cliente │         │ Cliente │
    │ 1, 2... │          │ 1, 2... │         │ 1, 2... │
    └─────────┘          └─────────┘         └─────────┘
```

### Separação de Responsabilidades

- **GameServer**: Gerencia conexões e múltiplos jogos
- **GameHandler**: Lógica de um jogo específico (perguntas, pontuações, coordenação)
- **DealWithClient**: Thread dedicada para cada cliente conectado
- **KahootClient**: Interface gráfica e comunicação com servidor

---

## 🧩 Componentes Principais

### 1. **GameServer** (`kahoot.server.GameServer`)

**Responsabilidade**: Ponto de entrada do servidor, gerencia conexões e jogos.

**Funcionalidades**:
- Aceita conexões de clientes na porta 8080
- Cria e gerencia múltiplos `GameHandler` (um por jogo)
- Thread pool com 10 threads para processar clientes
- Interface de comandos para criar/listar jogos

**Comandos**:
- `new <equipas> <jogadores_por_equipa> <num_perguntas>`: Cria novo jogo
- `list`: Lista jogos ativos
- `exit`: Encerra servidor

**Por que é Distribuído?**
- Comunica com clientes remotos via sockets TCP
- Cada cliente pode estar em máquina/processo diferente
- Serialização de objetos para comunicação

### 2. **GameHandler** (`kahoot.server.GameHandler`)

**Responsabilidade**: Gerencia o estado e lógica de um jogo específico.

**Estado Mantido**:
- `Game`: Objeto do jogo (equipas, jogadores, quiz)
- `connectedClients`: Map de clientes conectados
- `questionAnswers`: Respostas recebidas por pergunta
- `answeredPlayers`: Set de jogadores que já responderam
- `questionEnded`: Flags atômicas para controlar fim de perguntas

**Funcionalidades**:
- Adiciona jogadores a equipas
- Inicia jogo quando todas as equipas estão completas
- Envia perguntas a todos os jogadores
- Processa respostas e calcula pontuações
- Gerencia timers de 30 segundos por pergunta
- Alterna entre perguntas individuais e de equipa

**Por que é Concorrente?**
- Múltiplas threads (`DealWithClient`) acessam o mesmo `GameHandler`
- Respostas chegam simultaneamente de vários jogadores
- Necessita sincronização para evitar race conditions

### 3. **DealWithClient** (`kahoot.server.DealWithClient`)

**Responsabilidade**: Thread dedicada para comunicação com um cliente específico.

**Funcionalidades**:
- Recebe mensagens do cliente (`EnrollmentMessage`, `AnswerMessage`)
- Envia mensagens ao cliente (`QuestionMessage`, `ScoreMessage`, etc.)
- Gerencia timeout de conexão (60 segundos)
- Notifica `GameHandler` sobre desconexões

**Por que é Concorrente?**
- Uma thread por cliente = execução paralela
- Múltiplas threads acessam o mesmo `GameHandler` simultaneamente
- Thread pool gerencia todas as threads de clientes

**Por que é Distribuído?**
- Comunica via sockets TCP com cliente remoto
- Serialização de objetos para envio/receção de mensagens

### 4. **KahootClient** (`kahoot.client.KahootClient`)

**Responsabilidade**: Interface gráfica e comunicação com servidor.

**Componentes GUI**:
- `questionLabel`: Exibe pergunta atual
- `answerButtons[]`: 4 botões coloridos para respostas (A, B, C, D)
- `timerLabel`: Contador regressivo de 30 segundos
- `scoreLabel`: Pontuação atual do jogador
- `scoreboardArea`: Placar de todas as equipas

**Funcionalidades**:
- Conecta ao servidor via socket
- Envia mensagem de inscrição (`EnrollmentMessage`)
- Recebe e exibe perguntas
- Envia respostas quando jogador clica em botão
- Atualiza timer localmente (sincronizado com servidor)
- Exibe placares atualizados

**Por que é Distribuído?**
- Executa em processo/máquina diferente do servidor
- Comunicação via rede (sockets)

### 5. **Mecanismos de Coordenação**

#### **ModifiedCountdownLatch** (`kahoot.coordination.ModifiedCountdownLatch`)

**Responsabilidade**: Coordena respostas em perguntas individuais com bónus por velocidade.

**Como Funciona**:
- Contador inicializado com número total de jogadores
- Quando jogador responde corretamente, chama `countDown()`
- Os primeiros `bonusCount` jogadores recebem `bonusFactor` (ex: 2x pontos)
- Jogadores subsequentes recebem 1x pontos

**Exemplo**:
- 4 jogadores, `bonusCount=2`, `bonusFactor=2`
- Jogador 1 responde → `countDown()` → retorna 2 (bónus)
- Jogador 2 responde → `countDown()` → retorna 2 (bónus)
- Jogador 3 responde → `countDown()` → retorna 1 (sem bónus)
- Jogador 4 responde → `countDown()` → retorna 1 (sem bónus)

**Por que é Concorrente?**
- Múltiplas threads chamam `countDown()` simultaneamente
- Método `synchronized` garante thread-safety
- Usa `wait()` e `notifyAll()` para coordenação

#### **TeamBarrier** (`kahoot.coordination.TeamBarrier`)

**Responsabilidade**: Sincroniza respostas de todos os jogadores de uma equipa.

**Como Funciona**:
- Cada jogador chama `await()` quando responde
- Contador `arrived` incrementa
- Se `arrived == teamSize`: último jogador chegou, todos desbloqueiam
- Se timeout (30s): barreira quebra, todos desbloqueiam
- Primeiro jogador a chegar (position=1) calcula pontuação da equipa

**Por que é Concorrente?**
- Múltiplas threads (uma por jogador) chamam `await()` simultaneamente
- Usa `ReentrantLock` e `Condition` para sincronização
- Timeout com `await(timeout, TimeUnit)` evita bloqueio indefinido

**Por que usa Condition?**
- Permite espera eficiente (threads bloqueiam até sinal)
- `signalAll()` acorda todas as threads quando condição muda
- Mais eficiente que polling

---

## 🔄 Fluxo Completo do Jogo

### Fase 1: Inicialização do Servidor

```
1. GameServer inicia na porta 8080
2. Thread pool criado (10 threads)
3. Thread para aceitar conexões inicia
4. Thread para comandos do servidor inicia
5. Servidor aguarda comandos e conexões
```

### Fase 2: Criação de Jogo

```
1. Administrador executa: new 2 2 5
   (2 equipas, 2 jogadores/equipa, 5 perguntas)

2. GameServer cria novo GameHandler:
   - gameId = "game1"
   - Cria 2 equipas (Team1, Team2)
   - Carrega 5 perguntas aleatórias do JSON
   - Inicializa barreiras para cada equipa

3. Jogo fica em estado "aguardando jogadores"
```

### Fase 3: Conexão de Clientes

```
Para cada cliente que conecta:

1. Cliente cria socket para servidor (localhost:8080)
2. GameServer aceita conexão
3. Thread pool atribui thread para DealWithClient
4. DealWithClient cria ObjectInputStream/ObjectOutputStream
5. Cliente envia EnrollmentMessage:
   {
     gameId: "game1",
     teamId: "Team1",
     username: "Player1"
   }

6. DealWithClient recebe mensagem:
   - Busca GameHandler pelo gameId
   - Chama gameHandler.addPlayer(teamId, username, this)
   - GameHandler verifica se equipa tem espaço
   - Adiciona jogador à equipa
   - Verifica se todas as equipas estão completas

7. Se todas as equipas completas:
   - GameHandler.startGame() é chamado
   - gameInProgress = true
   - sendNextQuestion() envia primeira pergunta
```

### Fase 4: Execução do Jogo (Ronda por Ronda)

#### 4.1. Envio de Pergunta

```
1. GameHandler.sendNextQuestion():
   - Obtém próxima pergunta do quiz
   - Calcula questionIndex (0, 1, 2, ...)
   - Determina tipo: isTeamQuestion = (questionIndex % 2 == 1)
     * Índices pares (0,2,4...): Individual
     * Índices ímpares (1,3,5...): Equipa

2. Configura estruturas de coordenação:
   
   Se pergunta INDIVIDUAL:
   - Cria/reseta ModifiedCountdownLatch
   - Contador = total de jogadores
   - Bonus para primeiros 2 jogadores
   
   Se pergunta EQUIPA:
   - Reseta TeamBarrier de cada equipa
   - arrived = 0, broken = false

3. Inicializa estruturas de resposta:
   - questionAnswers[questionIndex] = novo Map
   - answeredPlayers[questionIndex] = novo Set
   - questionEnded[questionIndex] = AtomicBoolean(false)

4. Broadcast da pergunta:
   - Cria QuestionMessage com pergunta, tempo, índice
   - Para cada DealWithClient em connectedClients:
     * client.sendMessage(questionMessage)
   - Cada cliente recebe e exibe pergunta na GUI

5. Inicia timer de 30 segundos:
   - TimerTask agendado para 30s
   - Ao expirar, chama endQuestion() se ainda não terminou
```

#### 4.2. Receção de Respostas

**Cenário A: Pergunta Individual**

```
1. Jogador clica em botão de resposta (A, B, C ou D)
2. KahootClient.sendAnswer():
   - Para timer local
   - Desativa botões
   - Cria AnswerMessage:
     {
       gameId: "game1",
       teamId: "Team1",
       username: "Player1",
       questionIndex: 0,
       answer: 2  // índice da opção (0-3)
     }
   - Envia via ObjectOutputStream

3. DealWithClient recebe AnswerMessage:
   - Chama gameHandler.processAnswer(answerMsg)

4. GameHandler.processAnswer() [synchronized]:
   - Verifica se pergunta já terminou (questionEnded)
   - Verifica se jogador já respondeu (answeredPlayers)
   - Armazena resposta: questionAnswers[questionIndex].put(playerKey, answer)
   - Marca jogador como respondido: answeredPlayers.add(playerKey)
   - Verifica se resposta está correta

5. Se resposta CORRETA:
   - Obtém ModifiedCountdownLatch da pergunta
   - Chama latch.countDown():
     * Decrementa contador
     * Se currentBonus < bonusCount: retorna bonusFactor (2x)
     * Senão: retorna 1 (sem bónus)
   - Calcula pontos: question.getPoints() * bonus
   - Atualiza pontuação: updateScores(teamId, username, points)

6. Verifica se todas as respostas chegaram:
   - allAnswersReceived(questionIndex):
     * Compara answeredPlayers.size() com totalPlayers
   - Se todas chegaram:
     * Usa compareAndSet para garantir execução única
     * Chama endQuestion(questionIndex)
```

**Cenário B: Pergunta de Equipa**

```
1. Jogador clica em botão de resposta
2. KahootClient envia AnswerMessage (mesmo processo)

3. GameHandler.processAnswer():
   - Armazena resposta (mesmo processo)
   - Chama processTeamAnswer()

4. processTeamAnswer():
   - Obtém TeamBarrier da equipa do jogador
   - Chama barrier.await():
     * lock.lock()
     * arrived++ (incrementa contador)
     * position = arrived (posição na chegada)
     
     * Se arrived == teamSize:
       - Último jogador chegou
       - condition.signalAll() (acorda todos)
       - Retorna position
     
     * Senão:
       - Calcula tempo restante
       - condition.await(timeout, TimeUnit) (espera com timeout)
       - Se timeout: broken = true, signalAll()

5. Se position == 1 (primeiro jogador a chegar):
   - calculateTeamScore(teamId, question, questionIndex):
     * Para cada jogador da equipa:
       - Busca resposta: questionAnswers.get(teamId + "_" + username)
       - Verifica se está correta
     * Se TODOS acertaram: pontos = question.getPoints() * 2
     * Se ALGUNS acertaram: pontos = question.getPoints()
     * Se NINGUÉM acertou: pontos = 0
     * Atualiza pontuação da equipa

6. Verifica se todas as respostas chegaram:
   - Mesmo processo que pergunta individual
```

#### 4.3. Fim da Pergunta

```
1. endQuestion() é chamado quando:
   - Todas as respostas chegaram, OU
   - Timer de 30 segundos expirou

2. endQuestion() [synchronized]:
   - Cancela timer se ainda ativo
   - Marca pergunta como terminada: questionEnded.set(true)
   - broadcastScores(questionIndex):
     * Calcula pontuações de todas as equipas
     * Cria ScoreMessage para cada cliente
     * Envia via DealWithClient.sendMessage()
   
3. Clientes recebem ScoreMessage:
   - KahootClient atualiza placar na GUI
   - Exibe pontuações ordenadas

4. Após 5 segundos de pausa:
   - TimerTask agendado chama sendNextQuestion()
   - game.incrementQuestion()
   - Processo repete para próxima pergunta
```

#### 4.4. Fim do Jogo

```
1. Quando não há mais perguntas:
   - game.getQuiz().getNextQuestion() retorna null
   - endGame() é chamado

2. endGame():
   - gameInProgress = false
   - game.setGameEnded(true)
   - Calcula equipa vencedora (maior pontuação)
   - Cria GameEndMessage com resultados finais
   - Envia a todos os clientes

3. Clientes recebem GameEndMessage:
   - Exibem mensagem de fim de jogo
   - Mostram equipa vencedora
   - Desativam botões

4. Após 10 segundos:
   - GameHandler remove jogo do servidor
   - server.removeGame(gameId)
   - Recursos são liberados
```

---

## 🌐 Partes Distribuídas

### O que é Distribuído?

**Distribuição** = Comunicação entre processos/máquinas diferentes via rede.

### Componentes Distribuídos

#### 1. **Comunicação Cliente-Servidor**

**Por que é Distribuído?**
- Cliente e servidor executam em processos/máquinas diferentes
- Comunicação via sockets TCP (rede)
- Serialização de objetos para transmissão

**Como Funciona**:

```
Cliente (Máquina A)              Servidor (Máquina B)
     │                                 │
     │  ┌──────────────────────┐      │
     │  │ KahootClient          │      │
     │  │ - Socket              │      │
     │  │ - ObjectOutputStream  │      │
     │  │ - ObjectInputStream   │      │
     │  └──────────────────────┘      │
     │         │                       │
     │         │ TCP Socket            │
     │         │ (localhost:8080)      │
     │         │                       │
     │         ▼                       │
     │  ┌──────────────────────┐      │
     │  │ GameServer            │      │
     │  │ - ServerSocket        │      │
     │  │ - Aceita conexões     │      │
     │  └──────────────────────┘      │
     │                                 │
```

**Mensagens Serializadas**:
- `EnrollmentMessage`: Cliente → Servidor (inscrição)
- `AnswerMessage`: Cliente → Servidor (resposta)
- `QuestionMessage`: Servidor → Cliente (pergunta)
- `ScoreMessage`: Servidor → Cliente (placar)
- `GameEndMessage`: Servidor → Cliente (fim do jogo)
- `ErrorMessage`: Servidor → Cliente (erros)

**Serialização**:
- Todas as classes de mensagem implementam `Serializable`
- `ObjectOutputStream.writeObject()` serializa
- `ObjectInputStream.readObject()` deserializa
- Transmissão via TCP garante ordem e integridade

#### 2. **Múltiplos Jogos Simultâneos**

**Por que é Distribuído?**
- Cada jogo pode ter clientes em máquinas diferentes
- Servidor central coordena múltiplas sessões
- Distribuição lógica de jogos no servidor

**Como Funciona**:
```
GameServer
├── activeGames: Map<String, GameHandler>
│   ├── "game1" → GameHandler (4 clientes remotos)
│   ├── "game2" → GameHandler (6 clientes remotos)
│   └── "game3" → GameHandler (2 clientes remotos)
│
└── Cada GameHandler é isolado e independente
```

**Isolamento**:
- Cada `GameHandler` tem seu próprio estado
- Perguntas, pontuações e timers são independentes
- Clientes só veem o jogo ao qual pertencem

#### 3. **Timeout de Conexão**

**Por que é Distribuído?**
- Detecta clientes inativos em máquinas remotas
- Evita threads bloqueadas indefinidamente

**Implementação**:
```java
// DealWithClient construtor
/*
socket.setSoTimeout(60000); // 60 segundos

// No loop de receção
try {
    Object obj = in.readObject();
    // Processa mensagem
} catch (SocketTimeoutException e) {
    // Cliente inativo, mas mantém conexão
    continue;
}
```

---

## 🔀 Partes Concorrentes

### O que é Concorrente?

**Concorrência** = Múltiplas threads executando simultaneamente no mesmo processo, acessando dados partilhados.

### Componentes Concorrentes

#### 1. **Uma Thread por Cliente (DealWithClient)**

**Por que é Concorrente?**
- Cada cliente conectado tem sua própria thread
- Múltiplas threads executam em paralelo
- Todas acessam o mesmo `GameHandler`

**Estrutura**:
```
GameServer
└── Thread Pool (10 threads)
    ├── Thread 1 → DealWithClient (Cliente A)
    ├── Thread 2 → DealWithClient (Cliente B)
    ├── Thread 3 → DealWithClient (Cliente C)
    └── Thread 4 → DealWithClient (Cliente D)
         │
         └── Todas acessam → GameHandler (mesmo jogo)
```

**Problema de Concorrência**:
- Múltiplas threads chamam `processAnswer()` simultaneamente
- Todas atualizam `questionAnswers`, `answeredPlayers`, pontuações
- Necessita sincronização para evitar race conditions

#### 2. **GameHandler como Dados Partilhados**

**Por que é Concorrente?**
- `GameHandler` é acessado por múltiplas threads (`DealWithClient`)
- Estado partilhado: `questionAnswers`, `answeredPlayers`, pontuações
- Operações de leitura/escrita simultâneas

**Estruturas Partilhadas**:
```java
// Acessadas por múltiplas threads
private final Map<Integer, Map<String, Integer>> questionAnswers;
private final Map<Integer, Set<String>> answeredPlayers;
private final Map<String, DealWithClient> connectedClients;
private final Map<Integer, AtomicBoolean> questionEnded;
```

**Proteção**:
- `ConcurrentHashMap`: Thread-safe para operações básicas
- `synchronized` em métodos críticos (`processAnswer`, `addPlayer`)
- `AtomicBoolean` para flags de estado

#### 3. **Receção Simultânea de Respostas**

**Por que é Concorrente?**
- Vários jogadores respondem ao mesmo tempo
- Múltiplas threads processam respostas em paralelo
- Necessita coordenação para evitar condições de corrida

**Cenário**:
```
Tempo: 0s - Pergunta enviada
Tempo: 2s - Thread 1 processa resposta de Player1
Tempo: 2s - Thread 2 processa resposta de Player2 (simultâneo!)
Tempo: 3s - Thread 3 processa resposta de Player3
Tempo: 4s - Thread 4 processa resposta de Player4
```

**Sincronização**:
- `processAnswer()` é `synchronized`
- Apenas uma thread executa por vez
- Garante que respostas são processadas atomicamente

#### 4. **ModifiedCountdownLatch (Perguntas Individuais)**

**Por que é Concorrente?**
- Múltiplas threads chamam `countDown()` simultaneamente
- Necessita determinar ordem de chegada para bónus
- Thread-safe para evitar race conditions

**Implementação**:
```java
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
```

**Por que `synchronized`?**
- Garante que apenas uma thread executa `countDown()` por vez
- Evita que duas threads recebam o mesmo bónus
- `currentBonus` e `count` são atualizados atomicamente

#### 5. **TeamBarrier (Perguntas de Equipa)**

**Por que é Concorrente?**
- Múltiplas threads (uma por jogador) chamam `await()` simultaneamente
- Necessita sincronizar chegada de todos os jogadores
- Timeout para evitar bloqueio indefinido

**Implementação**:
```java
public int await() throws InterruptedException {
    lock.lock();
    try {
        arrived++;
        int position = arrived;
        
        if (arrived == teamSize) {
            condition.signalAll(); // Último chegou
            return position;
        }
        
        // Espera pelos outros
        while (arrived < teamSize && !broken) {
            condition.await(timeout, TimeUnit.MILLISECONDS);
        }
        
        return position;
    } finally {
        lock.unlock();
    }
}
```

**Por que `ReentrantLock` e `Condition`?**
- `Lock` permite controle fino sobre sincronização
- `Condition` permite espera eficiente (threads bloqueiam até sinal)
- `await(timeout)` evita bloqueio indefinido
- `signalAll()` acorda todas as threads quando condição muda

**Fluxo de Execução**:
```
Thread Player1: await() → arrived=1, position=1 → espera
Thread Player2: await() → arrived=2, position=2 → espera
Thread Player3: await() → arrived=3, position=3 → signalAll() → todos desbloqueiam
Thread Player4: await() → arrived=4, position=4 → signalAll() → todos desbloqueiam
```

#### 6. **Timer vs Processamento de Respostas**

**Por que é Concorrente?**
- Timer executa em thread separada
- Threads de clientes processam respostas
- Race condition: timer pode expirar enquanto respostas são processadas

**Problema**:
```
Thread Timer: Aguarda 30s → chama endQuestion()
Thread Cliente: Processa resposta → chama endQuestion()
→ Ambos podem chamar endQuestion() simultaneamente!
```

**Solução**:
```java
/*
// Timer
AtomicBoolean ended = questionEnded.get(questionIndex);
if (ended.compareAndSet(false, true)) {
    endQuestion(questionIndex); // Só uma thread executa
}

// processAnswer
if (allAnswersReceived(questionIndex)) {
    if (ended.compareAndSet(false, true)) {
        endQuestion(questionIndex); // Só uma thread executa
    }
}
```

**Por que `AtomicBoolean.compareAndSet()`?**
- Operação atômica: verifica e atualiza em uma única operação
- Garante que apenas uma thread consegue mudar de `false` para `true`
- Evita múltiplas execuções de `endQuestion()`

#### 7. **Thread Pool no GameServer**

**Por que é Concorrente?**
- Limita número de threads simultâneas
- Reutiliza threads para eficiência
- Gerencia execução paralela de múltiplos clientes

**Implementação**:
```java
private final ExecutorService threadPool = Executors.newFixedThreadPool(10);
```

**Comportamento**:
- Máximo de 10 threads simultâneas
- Novas conexões aguardam se pool estiver cheio
- Threads são reutilizadas após cliente desconectar

---

## 🔒 Sincronização e Thread-Safety

### Mecanismos de Sincronização Utilizados

#### 1. **synchronized Methods**

**Onde é usado?**
- `GameHandler.processAnswer()`: Processa respostas atomicamente
- `GameHandler.addPlayer()`: Adiciona jogadores atomicamente
- `ModifiedCountdownLatch.countDown()`: Atualiza contador atomicamente
- `Player.addScore()`: Atualiza pontuação atomicamente

**Por que?**
- Garante que apenas uma thread executa o método por vez
- Evita race conditions em operações críticas
- Simples e eficiente para métodos curtos

**Exemplo**:
```java
public synchronized void processAnswer(AnswerMessage answerMsg) {
    // Apenas uma thread executa por vez
    // Garante que respostas são processadas atomicamente
}
```

#### 2. **ConcurrentHashMap**

**Onde é usado?**
- `questionAnswers`: Map de respostas por pergunta
- `answeredPlayers`: Set de jogadores que responderam
- `connectedClients`: Map de clientes conectados
- `individualLatches`: Map de latches por pergunta
- `teamBarriers`: Map de barreiras por equipa

**Por que?**
- Thread-safe para operações básicas (get, put, containsKey)
- Não bloqueia toda a estrutura (melhor performance que `synchronized`)
- Suporta operações concorrentes de leitura/escrita

**Limitação**:
- Operações compostas ainda precisam de sincronização externa
- Exemplo: `if (!map.containsKey(key)) map.put(key, value)` não é atômico

#### 3. **AtomicBoolean**

**Onde é usado?**
- `questionEnded`: Flags para controlar fim de perguntas

**Por que?**
- Operações atômicas: `get()`, `set()`, `compareAndSet()`
- Thread-safe sem necessidade de `synchronized`
- `compareAndSet()` garante execução única

**Exemplo**:

AtomicBoolean ended = questionEnded.get(questionIndex);
if (ended.compareAndSet(false, true)) {
    // Apenas uma thread consegue executar este bloco
    endQuestion(questionIndex);
}


#### 4. **ReentrantLock e Condition**

**Onde é usado?**
- `TeamBarrier`: Sincronização de jogadores em perguntas de equipa

**Por que?**
- Controle fino sobre sincronização
- `Condition` permite espera eficiente (threads bloqueiam até sinal)
- `await(timeout)` evita bloqueio indefinido
- `signalAll()` acorda todas as threads quando condição muda

**Vantagens sobre `synchronized`**:
- Timeout explícito
- Múltiplas condições possíveis
- Mais flexível para lógica complexa

#### 5. **volatile**

**Onde é usado?**
- `Player.score`: Campo de pontuação

**Por que?**
- Garante visibilidade entre threads
- Mudanças são imediatamente visíveis a todas as threads
- Mais leve que `synchronized` para apenas leitura/escrita simples

**Nota**: `addScore()` ainda é `synchronized` porque a operação `+=` não é atômica.

### Prevenção de Race Conditions

#### 1. **Respostas Duplicadas**

**Problema**: Jogador pode enviar múltiplas respostas.

**Solução**:

Set<String> answered = answeredPlayers.get(questionIndex);
String playerKey = teamId + "_" + username;
if (!answered.add(playerKey)) {
    return; // Jogador já respondeu, ignorar
}


**Por que funciona?**
- `Set.add()` retorna `false` se elemento já existe
- Operação atômica em `ConcurrentHashMap.newKeySet()`
- Thread-safe

#### 2. **Múltiplas Execuções de endQuestion()**

**Problema**: Timer e processamento de respostas podem chamar `endQuestion()` simultaneamente.

**Solução**:
```java
AtomicBoolean ended = questionEnded.get(questionIndex);
if (ended.compareAndSet(false, true)) {
    endQuestion(questionIndex); // Só uma thread executa
}
```

**Por que funciona?**
- `compareAndSet()` é atômico
- Apenas uma thread consegue mudar de `false` para `true`
- Outras threads veem `true` e retornam

#### 3. **Armazenamento de Respostas**

**Problema**: Múltiplas threads armazenam respostas simultaneamente.

**Solução**:
```java
// Sempre usar playerKey para consistência
String playerKey = teamId + "_" + username;
questionAnswers.get(questionIndex).put(playerKey, answer);
```

**Por que funciona?**
- `ConcurrentHashMap.put()` é thread-safe
- Chave única por jogador evita sobrescrita acidental
- Consistente entre perguntas individuais e de equipa

### Tempos de Espera e Timeouts

#### 1. **Timer de Pergunta (30 segundos)**

**Onde**: `GameHandler.startQuestionTimer()`

**Como funciona**:
```java
questionTimer = new Timer();
questionTimer.schedule(new TimerTask() {
    @Override
    public void run() {
        synchronized (GameHandler.this) {
            AtomicBoolean ended = questionEnded.get(questionIndex);
            if (ended.compareAndSet(false, true)) {
                endQuestion(questionIndex);
            }
        }
    }
}, 30000); // 30 segundos
```

**Comportamento**:
- Timer inicia quando pergunta é enviada
- Após 30 segundos, chama `endQuestion()` se ainda não terminou
- Cancelado se todas as respostas chegarem antes

#### 2. **Timeout de Conexão (60 segundos)**

**Onde**: `DealWithClient` construtor

**Como funciona**:
```java
socket.setSoTimeout(60000); // 60 segundos
```

**Comportamento**:
- `readObject()` lança `SocketTimeoutException` se não receber dados em 60s
- Thread não fica bloqueada indefinidamente
- Conexão mantida, mas detecta clientes inativos

#### 3. **Timeout de TeamBarrier (30 segundos)**

**Onde**: `TeamBarrier.await()`

**Como funciona**:
```java
long elapsed = System.currentTimeMillis() - startTime;
long remaining = timeout - elapsed;

if (remaining <= 0) {
    broken = true;
    condition.signalAll();
    break;
}

condition.await(remaining, TimeUnit.MILLISECONDS);
```

**Comportamento**:
- Barreira quebra após 30 segundos
- Todas as threads desbloqueiam
- Equipa não recebe pontos se não todos responderam a tempo

#### 4. **Pausa Entre Perguntas (5 segundos)**

**Onde**: `GameHandler.endQuestion()`

**Como funciona**:
```java
new Timer().schedule(new TimerTask() {
    @Override
    public void run() {
        synchronized (GameHandler.this) {
            if (!game.isGameEnded()) {
                game.incrementQuestion();
                sendNextQuestion();
            }
        }
    }
}, 5000); // 5 segundos
```

**Comportamento**:
- Após enviar placar, aguarda 5 segundos
- Permite que jogadores vejam resultados
- Envia próxima pergunta automaticamente

#### 5. **Limpeza de Jogo (10 segundos)**

**Onde**: `GameHandler.endGame()`

**Como funciona**:
```java
new Timer().schedule(new TimerTask() {
    @Override
    public void run() {
        if (server != null) {
            server.removeGame(game.getGameId());
        }
    }
}, 10000); // 10 segundos
```

**Comportamento**:
- Após fim do jogo, aguarda 10 segundos
- Permite que clientes vejam resultados finais
- Remove jogo do servidor automaticamente

---

## 🚀 Como Executar

### Pré-requisitos

- Java JDK 8 ou superior
- Arquivo `quizzes.json` na raiz do projeto

### Compilação

```bash
# Compilar todo o projeto
javac -d out -sourcepath src src/kahoot/**/*.java src/Main.java

# Ou usar IDE (IntelliJ, Eclipse, etc.)
```

### Executar Servidor

```bash
# Terminal 1
cd out
java kahoot.server.GameServer

# Ou se estiver na raiz:
java -cp out kahoot.server.GameServer
```

**Comandos do Servidor**:
```
> new 2 2 5    # Cria jogo: 2 equipas, 2 jogadores/equipa, 5 perguntas
> list         # Lista jogos ativos
> exit         # Encerra servidor
```

### Executar Cliente

```bash
# Terminal 2 (e mais terminais para outros jogadores)
cd out
java kahoot.client.KahootClient localhost 8080 game1 Team1 Player1

# Parâmetros:
# 1. IP do servidor (localhost ou IP remoto)
# 2. Porta (8080)
# 3. gameId (game1, game2, etc.)
# 4. teamId (Team1, Team2, etc.)
# 5. username (qualquer nome único)
```

### Exemplo Completo

**Terminal 1 (Servidor)**:
```bash
java kahoot.server.GameServer
> new 2 2 5
🎮 JOGO CRIADO: game1
   Equipas: 2
   Jogadores por equipa: 2
   Perguntas: 5
   Use este ID nos clientes: game1
```

**Terminal 2 (Cliente 1)**:
```bash
java kahoot.client.KahootClient localhost 8080 game1 Team1 Player1
```

**Terminal 3 (Cliente 2)**:
```bash
java kahoot.client.KahootClient localhost 8080 game1 Team1 Player2
```

**Terminal 4 (Cliente 3)**:
```bash
java kahoot.client.KahootClient localhost 8080 game1 Team2 Player3
```

**Terminal 5 (Cliente 4)**:
```bash
java kahoot.client.KahootClient localhost 8080 game1 Team2 Player4
```

Quando 4 jogadores conectarem, o jogo inicia automaticamente!

---

## 📊 Resumo: Concorrência vs Distribuição

### ✅ Partes Distribuídas

| Componente | Por quê? | Como? |
|------------|----------|-------|
| Cliente-Servidor | Processos/máquinas diferentes | Sockets TCP, serialização |
| Múltiplos Jogos | Sessões distribuídas | Map de GameHandlers isolados |
| Mensagens | Comunicação remota | ObjectInputStream/OutputStream |
| Timeout de Conexão | Detecta inatividade remota | `socket.setSoTimeout()` |

### ✅ Partes Concorrentes

| Componente | Por quê? | Como? |
|------------|----------|-------|
| Thread por Cliente | Execução paralela | Thread pool, `DealWithClient` |
| GameHandler Partilhado | Múltiplas threads acessam | `synchronized`, `ConcurrentHashMap` |
| Respostas Simultâneas | Vários jogadores respondem | `synchronized processAnswer()` |
| ModifiedCountdownLatch | Bónus por velocidade | `synchronized countDown()` |
| TeamBarrier | Sincronização de equipa | `ReentrantLock`, `Condition` |
| Timer vs Respostas | Race condition | `AtomicBoolean.compareAndSet()` |

---

## 🎓 Conceitos Demonstrados

Este projeto demonstra os seguintes conceitos de PCD:

1. **Comunicação em Rede**: Sockets TCP, serialização de objetos
2. **Threads**: Criação, sincronização, thread pool
3. **Sincronização**: `synchronized`, `Lock`, `Condition`, `AtomicBoolean`
4. **Estruturas Thread-Safe**: `ConcurrentHashMap`, `AtomicBoolean`
5. **Coordenação**: `CountDownLatch`, `Barrier` (implementações customizadas)
6. **Race Conditions**: Prevenção e resolução
7. **Timeouts**: Gestão de tempo e bloqueios
8. **Arquitetura Cliente-Servidor**: Separação de responsabilidades

---

## 📝 Notas Finais

- **Thread-Safety**: Todos os pontos críticos estão protegidos
- **Robustez**: Timeouts e tratamento de erros implementados
- **Escalabilidade**: Suporta múltiplos jogos e clientes simultâneos
- **Isolamento**: Cada jogo é independente e isolado
- **Coordenação**: Mecanismos adequados para perguntas individuais e de equipa

Este projeto serve como exemplo completo de como implementar um sistema distribuído e concorrente em Java, demonstrando boas práticas de sincronização e coordenação de threads.

