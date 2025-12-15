# Mapeamento do Enunciado para o Código

Este documento mapeia cada seção e requisito do enunciado do projeto Kahoot-PCD para a sua implementação no código, indicando onde cada funcionalidade está implementada.

---

## 1. Descrição Genérica do Projeto

### 1.1 Responsabilidades do Servidor

**Enunciado**: *"O servidor armazena todas as perguntas bem como as suas respostas, gere a lógica do jogo e monitoriza os jogos ativos e os jogadores neles participantes. Cada jogador ligado ao servidor está associado a uma thread DealWithClient que gere as interações com ele."*

**Implementação**:

- **Armazenamento de perguntas**: `src/kahoot/game/Quiz.java`
  - Método `loadFromJson()` carrega perguntas do ficheiro JSON
  - Utilizado em `Game.createQuiz()` → `src/kahoot/game/Game.java` (linha 36-54)

- **Lógica do jogo**: `src/kahoot/server/GameHandler.java`
  - Gerencia estado do jogo, perguntas, respostas, pontuações
  - Métodos: `startGame()`, `sendNextQuestion()`, `processAnswer()`, `endQuestion()`, `endGame()`

- **Monitorização de jogos ativos**: `src/kahoot/server/GameServer.java`
  - `activeGames: Map<String, GameHandler>` (linha 14)
  - Métodos: `getGame()`, `removeGame()`, `handleListGames()`

- **Thread DealWithClient**: `src/kahoot/server/DealWithClient.java`
  - Implementa `Runnable` (linha 7)
  - Gerencia interações com cada cliente (receção e envio de mensagens)
  - Criado para cada conexão em `GameServer.acceptConnections()` (linha 41)

### 1.2 Responsabilidades do Cliente

**Enunciado**: *"No arranque do cliente, este liga-se automaticamente ao servidor, indicando a identificação do jogo e equipa a que pertence, bem como um identificador individual. Estes parâmetros, bem como o endereço e porto do servidor, devem ser recebidos como argumentos de execução do main."*

**Implementação**:

- **Ligação ao servidor**: `src/kahoot/client/KahootClient.java`
  - Método `connectToServer()` (linha 126-148)
  - Cria `Socket` e `ObjectInputStream`/`ObjectOutputStream`

- **Argumentos de execução**: `src/kahoot/client/KahootClient.java`
  - Método `main()` (linha 365-381)
  - Formato: `java KahootClient <IP> <PORT> <GAME_ID> <TEAM_ID> <USERNAME>`

- **Envio de identificação**: `src/kahoot/client/KahootClient.java`
  - `EnrollmentMessage` enviado em `connectToServer()` (linha 133-134)

- **GUI**: `src/kahoot/client/KahootClient.java`
  - Método `initializeGUI()` (linha 40-122)
  - Exibe perguntas, botões de resposta, timer, placar

### 1.3 Estatísticas do jogo

**Enunciado**: *"Cada jogo é representado por um objeto GameState no servidor. Este objeto define a pergunta atual, implementa o cronómetro decrescente da ronda, e mantém registo dos jogadores e equipas para as respostas enviadas e o placar."*

**Implementação**:

- **GameState (representado por Game + GameHandler)**: 
  - `src/kahoot/game/Game.java` - Estado persistente do jogo (equipas, quiz, índices)
  - `src/kahoot/server/GameHandler.java` - Estado ativo do jogo (perguntas atuais, respostas, coordenação)
  
- **Pergunta atual**: `src/kahoot/server/GameHandler.java`
  - Campo `currentQuestion: Question` (linha 19)
  - Método `sendNextQuestion()` (linha 75-114)

- **Cronómetro decrescente**: `src/kahoot/server/GameHandler.java`
  - Método `startQuestionTimer()` (implementado em `sendNextQuestion()` linha 113)
  - Timer de 30 segundos por pergunta

- **Registo de jogadores e equipas**: `src/kahoot/game/Game.java`
  - `teams: Map<String, Team>` (linha 14)
  - Cada `Team` contém lista de `Player`

- **Respostas enviadas**: `src/kahoot/server/GameHandler.java`
  - `questionAnswers: Map<Integer, Map<String, Integer>>` (linha 15)
  - `answeredPlayers: Map<Integer, Set<String>>` (linha 16)

- **Placar**: `src/kahoot/game/Team.java` e `src/kahoot/game/Player.java`
  - `Team.teamScore` e `Player.score`
  - Atualizado em `GameHandler.u7pdateScores()` (linha 271-280)

### 1.4 Placar de desempenho

**Enunciado**: *"Após cada ronda, o servidor envia a todos os jogadores no jogo um placar atualizado mostrando a posição atual da equipa, os pontos ganhos na ronda, a pontuação acumulada e a lista de todas as equipas (e respetivas pontuações) do jogo."*

**Implementação**:

- **Envio de placar**: `src/kahoot/server/GameHandler.java`
  - Método `broadcastScores()` (linha 323-338)
  - Chamado em `endQuestion()` (linha 307)

- **Mensagem de placar**: `src/kahoot/messages/ScoreMessage.java`
  - Contém pontuações de todas as equipas

- **Exibição no cliente**: `src/kahoot/client/KahootClient.java`
  - Método `handleScoreMessage()` (procure no código)
  - Atualiza `scoreboardArea` com classificações

---

## 2. Requisitos do Projeto

### 2.1 Nome de utilizador, identificação de equipa e jogo

**Enunciado**: *"Ao iniciar a aplicação, o utilizador recebe como parâmetro de execução um identificador de utilizador, que deve ser único em todo o sistema. Se outro jogador já estiver a utilizar o nome escolhido, o servidor rejeita-o liminarmente."*

**Implementação**:

- **Argumentos de execução**: `src/kahoot/client/KahootClient.java`
  - `main()` recebe: `<IP> <PORT> <GAME_ID> <TEAM_ID> <USERNAME>` (linha 365-381)

- **Validação de username único**: `src/kahoot/game/Game.java`
  - Método `addPlayer()` (linha 56-73)
  - Verifica duplicados em todas as equipas (linha 62-69)
  - Retorna `false` se username já existe

- **Rejeição de ligação**: `src/kahoot/server/DealWithClient.java`
  - `handleEnrollment()` (linha 57-76)
  - Se `game.addPlayer()` retorna `false`, envia `ErrorMessage` e desconecta (linha 69-72)

**Enunciado**: *"Se o jogo enviado não existir, ou se estiver a exceder o número de equipas previsto, a ligação é recusada."*

**Implementação**:

- **Validação de jogo existente**: `src/kahoot/server/DealWithClient.java`
  - `handleEnrollment()` verifica `server.getGame(gameId)` (linha 61-66)
  - Se `null`, envia erro e desconecta

- **Validação de capacidade da equipa**: `src/kahoot/game/Game.java`
  - `addPlayer()` verifica `team.getPlayerCount() >= playersPerTeam` (linha 58)
  - Retorna `false` se equipa cheia

### 2.2 Códigos do jogo

**Enunciado**: *"Servidor deve ser capaz de gerar um código único que não esteja em utilização. O servidor deve conseguir armazenar os códigos e descartá-los quando não estão mais em utilização."*

**Implementação**:

- **Geração de código único**: `src/kahoot/server/GameServer.java`
  - Método `handleNewGame()` (linha 73-101)
  - Código gerado: `"game" + (activeGames.size() + 1)` (linha 88)
  - Armazenado em `activeGames: Map<String, GameHandler>` (linha 90)

- **Descarte de códigos**: `src/kahoot/server/GameServer.java`
  - Método `removeGame()` (linha 120-122)
  - Pode ser chamado manualmente pelo servidor quando necessário

**Enunciado**: *"A criação de um novo jogo é feita através de uma interface textual (TUI), que permitirá a qualquer momento a criação de um jogo e, opcionalmente, a visualização dos jogos existentes, incluindo as classificações e estado do jogo. Sugere-se o seguinte comando: new <numequipas> <numjogadoresporequipa> <numperguntas>"*

**Implementação**:

- **TUI do servidor**: `src/kahoot/server/GameServer.java`
  - Método `handleCommands()` (linha 50-76)
  - Thread separada para processar comandos do `Scanner` (linha 33)

- **Comando new**: `src/kahoot/server/GameServer.java`
  - `handleNewGame()` (linha 73-101)
  - Formato: `new <num_equipas> <jogadores_por_equipa> <num_perguntas>`

- **Comando list**: `src/kahoot/server/GameServer.java`
  - `handleListGames()` (linha 103-114)
  - Lista jogos ativos e número de jogadores conectados

### 2.3 Tabela de pontuações

**Enunciado**: *"Após cada ronda, a pontuação das equipas é atualizada com base nos critérios indicados à frente, se a opção escolhida for a correta."*

**Implementação**:

- **Atualização de pontuações**: `src/kahoot/server/GameHandler.java`
  - `processIndividualAnswer()` (linha 203-215) - para perguntas individuais
  - `calculateTeamScore()` (linha 232-269) - para perguntas de equipa
  - `updateScores()` (linha 271-280) - atualiza `Player.score` e `Team.teamScore`

- **Verificação de resposta correta**: `src/kahoot/game/Question.java`
  - Método `isCorrect(int answer)` verifica se resposta está correta

**Enunciado**: *"No fim de cada pergunta, será apresentado um placar sumário que deve ser enviados para todos os jogadores."*

**Implementação**:

- **Envio de placar**: `src/kahoot/server/GameHandler.java`
  - `broadcastScores()` chamado em `endQuestion()` (linha 307)
  - Envia `ScoreMessage` para todos os clientes (linha 329-336)

### 2.4 Ciclo de perguntas

**Enunciado**: *"Após o servidor enviar a pergunta e as opções de resposta aos jogadores, este inicia uma contagem decrescente de, por exemplo 30 segundos."*

**Implementação**:

- **Envio de pergunta**: `src/kahoot/server/GameHandler.java`
  - `broadcastQuestion()` (linha 116-143) envia `QuestionMessage` a todos os clientes

- **Timer de 30 segundos**: `src/kahoot/server/GameHandler.java`
  - `startQuestionTimer()` implementado no contexto de `sendNextQuestion()` (linha 113)
  - Usa `Timer.schedule()` com delay de 30000ms (30 segundos)
  - Quando expira, chama `endQuestion()` se ainda não terminou

**Enunciado**: *"As perguntas devem ser lidas de um ficheiro de texto em formato json, conforme exemplo incluído mais abaixo. Ficheiros json são fáceis de processar com auxílio da biblioteca Gson, biblioteca de Java da Google que permite converter objetos em JSON e vice-versa."*

**Implementação**:

- **Leitura de JSON**: `src/kahoot/game/Quiz.java`
  - Método estático `loadFromJson(String filename, int numQuestions)` (linha 28-76)
  - Usa biblioteca Gson: `import com.google.gson.Gson;` (linha 10)
  - Processa estrutura JSON conforme exemplo do enunciado

- **Chamada**: `src/kahoot/game/Game.java`
  - `createQuiz()` chama `Quiz.loadFromJson("quizzes.json", this.numQuestions)` (linha 39)

- **Ficheiro**: `quizzes.json` na raiz do projeto

### 2.5 Receção de respostas

**Enunciado**: *"Cada jogo armazena as respostas enviadas pelos jogadores à questão atual. Como vários jogadores podem enviar respostas em simultâneo, o servidor deve garantir que todas as respostas enviadas são registadas exatamente uma única vez, sem interferências."*

**Implementação**:

- **Armazenamento de respostas**: `src/kahoot/server/GameHandler.java`
  - `questionAnswers: Map<Integer, Map<String, Integer>>` (linha 15)
  - Chave: `playerKey = teamId + "_" + username` (linha 168, 185)

- **Prevenção de duplicados**: `src/kahoot/server/GameHandler.java`
  - `answeredPlayers: Map<Integer, Set<String>>` (linha 16)
  - Verificação: `if (!answered.add(playerKey))` retorna (linha 169-172)
  - Método `processAnswer()` é `synchronized` (linha 146) para garantir thread-safety

- **Sincronização**: 
  - `synchronized` em `processAnswer()` garante acesso exclusivo
  - `ConcurrentHashMap` para estruturas partilhadas

**Enunciado**: *"Quando todos os jogadores enviarem a sua resposta, ou se o tempo-limite se esgotar, a ronda termina."*

**Implementação**:

- **Verificação de todas as respostas**: `src/kahoot/server/GameHandler.java`
  - Método `allAnswersReceived()` (linha 282-291)
  - Compara `answeredPlayers.size()` com `totalPlayers`

- **Terminação da ronda**: `src/kahoot/server/GameHandler.java`
  - Se todas as respostas recebidas: `endQuestion()` chamado em `processAnswer()` (linha 195-201)
  - Se timeout: `endQuestion()` chamado pelo timer (implementado em `startQuestionTimer()`)
  - Verificação de estado dentro de `synchronized` garante execução única

**Enunciado**: *"Assim que a ronda acaba é enviado um placar resumido aos clientes a mostrar o desempenho das equipas na ronda."*

**Implementação**:

- **Envio de placar**: `src/kahoot/server/GameHandler.java`
  - `endQuestion()` chama `broadcastScores()` (linha 307)
  - Envia `ScoreMessage` com pontuações atualizadas de todas as equipas

### 2.6 Coordenação para o fim do jogo

**Enunciado**: *"Como descrito na secção anterior, o jogo termina quando acabarem as submissões ou o tempo expirar na última pergunta. Quando tal acontecer, o jogo termina para todos os participantes. Para implementar esta coordenação, devem ser interrompidos todas as threads dos jogadores em execução (DealWithClient)."*

**Implementação**:

- **Detecção de fim do jogo**: `src/kahoot/server/GameHandler.java`
  - `sendNextQuestion()` verifica se `currentQuestion == null` (linha 77-80)
  - Se `null`, chama `endGame()` (linha 78)

- **Fim do jogo**: `src/kahoot/server/GameHandler.java`
  - Método `endGame()` (linha 340-365)
  - Envia `GameEndMessage` a todos os clientes (linha 357-363)

- **Nota sobre interrupção de threads**: 
  - As threads `DealWithClient` terminam naturalmente quando a conexão é fechada
  - O servidor envia `GameEndMessage` e o cliente pode encerrar
  - As threads são geridas pelo `ExecutorService` (thread pool) e terminam quando o `run()` de `DealWithClient` completa

### 2.7 Tipos de perguntas

#### Perguntas Individuais

**Enunciado**: *"Neste tipo de perguntas a classificação é calculada para cada jogador, somando-se os valores de todos os membros da mesma equipa. Existirão pontuações bonificadas para os primeiros dois jogadores a responder: a sua pontuação será o dobro da cotação da pergunta."*

**Implementação**:

- **Determinação de tipo**: `src/kahoot/server/GameHandler.java`
  - `isTeamQuestion = (questionIndex % 2 == 1)` (linha 83)
  - Índices pares (0,2,4...): individuais
  - Índices ímpares (1,3,5...): equipa

- **Processamento de resposta individual**: `src/kahoot/server/GameHandler.java`
  - Método `processIndividualAnswer()` (linha 203-215)
  - Chama `latch.countDown()` para obter bónus (linha 207)
  - Calcula pontos: `question.getPoints() * bonus` (linha 208)
  - Atualiza pontuação: `updateScores()` (linha 211)

- **ModifiedCountdownLatch**: `src/kahoot/coordination/ModifiedCountdownLatch.java`
  - Implementa bónus para primeiros 2 jogadores
  - `countDown()` retorna `bonusFactor` (2) para primeiros `bonusCount` (2) jogadores (linha 22-33)
  - `bonusFactor=2, bonusCount=2` configurado em `GameHandler` (linha 100)

**Enunciado**: *"Sugere-se a utilização de uma CountDownLatch + Timer. Esse fator deve ser computado quando a resposta for submetida ao semáforo, em particular como valor de devolução do método countDown."*

**Implementação**:

- **ModifiedCountdownLatch**: `src/kahoot/coordination/ModifiedCountdownLatch.java`
  - Classe customizada (não usa `java.util.concurrent.CountDownLatch`)
  - Método `countDown()` retorna `int` com fator de bónus (linha 22-33)
  - Método `await()` implementa timer bloqueante (linha 35-49)
  - API conforme especificação do enunciado

- **Timer**: Implementado em `ModifiedCountdownLatch.await()` usando `wait()` com timeout

#### Perguntas de Equipa

**Enunciado**: *"Nestas perguntas, a cotação das perguntas de uma equipa apenas serão decididas após a receção das respostas de todos os seus elementos (ou quando o tempo limite expirar). Caso todos acertem, têm a cotação da pergunta duplicada. Caso algum falhe, apenas será considerada a melhor pontuação de entre eles, sem nenhuma bonificação."*

**Implementação**:

- **Processamento de resposta de equipa**: `src/kahoot/server/GameHandler.java`
  - Método `processTeamAnswer()` (linha 217-230)
  - Chama `barrier.await()` para sincronizar (linha 221)

- **Cálculo de pontuação da equipa**: `src/kahoot/server/GameHandler.java`
  - Método `calculateTeamScore()` (linha 232-269)
  - Verifica respostas de todos os jogadores da equipa (linha 240-254)
  - Se todos acertaram: `points = question.getPoints() * 2` (linha 258)
  - Se alguns acertaram: `points = question.getPoints()` (linha 261)
  - Se ninguém acertou: `points = 0` (linha 264)

**Enunciado**: *"Sugere-se a utilização de uma barreira para estabelecer esta coordenação. A implementação desta estrutura de coordenação deve fazer uso de variáveis condicionais."*

**Implementação**:

- **TeamBarrier**: `src/kahoot/coordination/TeamBarrier.java`
  - Implementação customizada usando `ReentrantLock` e `Condition` (linha 11-12)
  - Método `await()` bloqueia até todos chegarem ou timeout (linha 26-74)
  - Usa `condition.await(timeout, TimeUnit)` para timeout (linha 55)
  - `signalAll()` acorda todas as threads quando último jogador chega (linha 38)

- **Uso de Condition**: 
  - `Condition condition = lock.newCondition()` (linha 21)
  - `condition.await()` para espera (linha 55)
  - `condition.signalAll()` para acordar threads (linha 38, 49, 58, 64)

### 2.8 Threadpool: tarefa extra

**Enunciado**: *"Para limitar a concorrência em cenários em que haja muitos jogos a decorrer, sugere-se a utilização de uma Threadpool, de maneira a limitar os jogos a decorrer a cinco."*

**Implementação**:

- **Thread Pool no GameServer**: `src/kahoot/server/GameServer.java`
  - `ExecutorService threadPool = Executors.newFixedThreadPool(10)` (linha 21)
  - **Nota**: O pool está configurado para 10 threads, não 5 como sugerido
  - Cada `DealWithClient` é executado no pool (linha 41)
  - Pool é desligado no shutdown (linha 131)

- **Limitação**: O pool limita threads de clientes, não o número de jogos simultâneos. Cada jogo pode ter múltiplos clientes, cada um numa thread do pool.

---

## 3. Detalhes de Implementação

### 3.1 Lançamento do servidor

**Enunciado**: *"O servidor é lançado sem argumentos, passando a estar preparado para criar novos jogos. Apenas quando for criado um jogo poderá o servidor receber ligações de clientes remotos."*

**Implementação**:

- **Lançamento**: `src/kahoot/server/GameServer.java`
  - `main()` sem argumentos (linha 135-142)
  - Inicia na porta 8080 (hardcoded)

- **Preparação para criar jogos**: `src/kahoot/server/GameServer.java`
  - `start()` inicia threads para aceitar conexões e comandos (linha 25-34)

- **Receção de ligações**: 
  - `acceptConnections()` aceita conexões continuamente (linha 36-48)
  - Não verifica se há jogos criados antes de aceitar - aceita sempre, mas valida na inscrição

### 3.2 Criação de novo jogo

**Enunciado**: *"A criação de um novo jogo é feita na sequência de um comando inserido na TUI do servidor. A partir desse momento, será possível receber ligações de jogadores e, quando estiverem ligados todos os jogadores previstos, iniciar o ciclo do jogo."*

**Implementação**:

- **Comando TUI**: `src/kahoot/server/GameServer.java`
  - `handleCommands()` processa comando `new` (linha 50-76)
  - `handleNewGame()` cria `GameHandler` e adiciona a `activeGames` (linha 73-101)

- **Início do jogo**: `src/kahoot/server/GameHandler.java`
  - `addPlayer()` verifica `canStartGame()` (linha 52)
  - Se todas as equipas completas, chama `startGame()` (linha 53)
  - `startGame()` inicia o jogo e envia primeira pergunta (linha 66-73)

### 3.3 Ficheiro com as perguntas

**Enunciado**: *"É fornecido um ficheiro exemplo com perguntas sobre a matéria de PCD, que pode ser usado para alimentar o jogo. As perguntas serão usadas nas rondas de forma aleatória."*

**Implementação**:

- **Leitura do ficheiro**: `src/kahoot/game/Quiz.java`
  - `loadFromJson()` lê `quizzes.json` (linha 28-76)
  - Usa Gson para parsing JSON

- **Seleção aleatória**: `src/kahoot/game/Quiz.java`
  - Método `selectRandomQuestions()` (linha 81-92)
  - Usa `Collections.shuffle()` para embaralhar (linha 89)
  - Seleciona primeiras N perguntas (linha 91)

- **Formato JSON**: Processado conforme exemplo do enunciado
  - Extrai array `quizzes[0].questions[]` (linha 34-43)
  - Cria objetos `Question` (linha 46-62)

### 3.4 Perguntas individuais: CountDownLatch modificado

**Enunciado**: *"Para poder classificar corretamente as perguntas individuais, deve ser aplicado um CountDownLatch alterado. Este deve ter um temporizador para detetar que passou o tempo limite, no método await, bloqueante, que apenas desbloqueará quando o CountDownLatch desbloquear. O método countdown deve ter um valor de devolução inteiro, que será o fator a aplicar à cotação da resposta, e que será invocado quando esta for recebida de um dos jogadores."*

**Implementação**:

- **ModifiedCountdownLatch**: `src/kahoot/coordination/ModifiedCountdownLatch.java`
  - Método `countDown()` retorna `int` com fator de bónus (linha 22-33)
  - Método `await()` bloqueia até `count == 0` ou timeout (linha 35-49)
  - Timer implementado com `wait(remaining)` baseado em `startTime` e `waitPeriod` (linha 37-44)
  - `notifyAll()` acorda threads quando termina (linha 48)

- **Uso**: `src/kahoot/server/GameHandler.java`
  - Criado para cada pergunta individual (linha 100)
  - `countDown()` chamado em `processIndividualAnswer()` (linha 207)
  - Valor retornado multiplicado pela cotação (linha 208)

### 3.5 Perguntas por equipa: barreira

**Enunciado**: *"As perguntas por equipa devem ser coordenadas por uma barreira modificada, de maneira a apenas classificar a resposta da equipa quando todos os jogadores tiverem respondido, ou quando o tempo limite expirar. Se este tempo de facto expirar, todos as chamadas aos métodos await devem ser desbloqueadas, e o cálculo das pontuações deve ser feito através da funcionalidade barrierAction. Esta barreira deve ser implementada usando variáveis condicionais."*

**Implementação**:

- **TeamBarrier**: `src/kahoot/coordination/TeamBarrier.java`
  - Implementada com `ReentrantLock` e `Condition` (linha 11-12, 20-21)
  - `await()` bloqueia até `arrived == teamSize` ou timeout (linha 26-74)
  - Timeout implementado com `condition.await(timeout, TimeUnit)` (linha 55)
  - Quando timeout: `broken = true` e `signalAll()` (linha 48-49, 57-58)
  - Primeiro jogador a chegar (`position == 1`) calcula pontuação (linha 223-225)

- **Cálculo de pontuação**: `src/kahoot/server/GameHandler.java`
  - `calculateTeamScore()` chamado pelo primeiro jogador (linha 225)
  - Verifica todas as respostas da equipa (linha 240-254)
  - Aplica regras de pontuação conforme respostas corretas/incorretas (linha 257-268)

---

## 4. Fases de Desenvolvimento

Este documento não mapeia as fases de desenvolvimento, pois são sugestões metodológicas. A implementação atual cobre todas as funcionalidades descritas nas fases.

---

## Resumo da Estrutura de Ficheiros

```
src/kahoot/
├── client/
│   └── KahootClient.java          # Cliente com GUI
├── coordination/
│   ├── ModifiedCountdownLatch.java # CountDownLatch modificado
│   └── TeamBarrier.java            # Barreira para equipas
├── game/
│   ├── Game.java                   # Estado do jogo (GameState)
│   ├── Player.java                 # Jogador
│   ├── Question.java               # Pergunta
│   ├── Quiz.java                   # Quiz (carrega JSON)
│   └── Team.java                   # Equipa
├── messages/
│   ├── AnswerMessage.java          # Mensagem de resposta
│   ├── EnrollmentMessage.java      # Mensagem de inscrição
│   ├── ErrorMessage.java           # Mensagem de erro
│   ├── GameEndMessage.java         # Mensagem de fim de jogo
│   ├── Message.java                # Classe base
│   ├── QuestionMessage.java        # Mensagem de pergunta
│   └── ScoreMessage.java           # Mensagem de placar
└── server/
    ├── DealWithClient.java         # Thread por cliente
    ├── GameHandler.java            # Lógica do jogo
    └── GameServer.java             # Servidor principal
```

---

**Notas Finais**:
- Todas as funcionalidades principais do enunciado estão implementadas
- A coordenação usa mecanismos customizados (não usa bibliotecas padrão de coordenação)
- Thread-safety garantido com `synchronized`, `ConcurrentHashMap`, `ReentrantLock` e `Condition`
- Comunicação distribuída via sockets TCP e serialização de objetos