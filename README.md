# AlkaTime

Contagem de tempo online, missões de recompensa e ranking de tempo jogado para a
rede Alka* (Paper 1.21.8 / Java 21) — construído sobre o AlkaCore (banco/GUI
compartilhados) e a AlkaEconomy (moeda `ticks`).

## O que faz

- **Contador 100% assíncrono** (`PlayerTimeManager`): cada jogador tem um
  `baseline` (total persistido antes da sessão atual) + `sessionStart`, e o
  tempo "ao vivo" é sempre `baseline + (agora - sessionStart)`, nunca uma
  query nova no banco. O carregamento inicial do `baseline` só libera o
  `sessionStart` depois de terminar (os dois writes acontecem na mesma task
  assíncrona, em sequência) — elimina qualquer corrida entre esse load e um
  autosave/quit que tentasse persistir um total incompleto.
- **Autosave periódico** (`autosave-interval-minutes` no `config.yml`) — protege
  contra crash do processo sem depender só do quit. Salva também de forma
  síncrona e bloqueante no `onDisable`, antes do AlkaCore fechar o pool de
  conexões.
- **Missões infinitas de recompensa** (`rewards.yml`) — uma seção por
  quantidade de segundos necessária, quantas o admin quiser. Cada missão paga
  uma moeda da AlkaEconomy (padrão `ticks`, reservada para tempo online) mais
  uma lista opcional de comandos extras via console.
- **Menu principal** (`/tempo`, `BaseGui` do AlkaCore) — tempo jogado + grade
  de missões (bloqueada/disponível/coletada) + botão pro TOP.
- **TOP de tempo online** (`/tempo top`) — ranking paginado, lido direto do
  banco (reflete jogadores offline também), com botão "Voltar" pro menu
  principal.
- **NPC configurável** (`/alkatime setnpc`/`delnpc`) via **Citizens**
  (soft-dependency, 100% via reflexão — sem artefato Maven público confiável)
  com **holograma opcional** via **DecentHolograms** (soft-dependency,
  `compileOnly` direto via DHAPI — mesmo hologram plugin já usado pelo
  AlkaMines na rede) mostrando o TOP 1 atualizado no mesmo ciclo do autosave.
- **API pública** (`AlkaTimeAPI`, registrada no `ServicesManager`,
  `CompletableFuture<Long> getOnlineSeconds(UUID)`) — bate exatamente com o
  hook de reflexão que o AlkaRankUp já tinha escrito (`TimeHook`) esperando
  este plugin nascer, para o requisito `online_time` dos ranks.
- **Comandos**: `/tempo` (+`top`, +`<jogador>`, PT-BR) para jogador;
  `/alkatime setnpc|delnpc|reload|set|add|remove|reset` (inglês, admin) —
  `set`/`add`/`remove` aceitam tanto segundos puros ("3600") quanto formato
  composto ("1h30m", "2d").
- **PlaceholderAPI** (`alkatime`): `%alkatime_tempo%`, `%alkatime_tempo_raw%`,
  `%alkatime_horas%`, `%alkatime_top_player_<n>%`, `%alkatime_top_value_<n>%`
  — os placeholders de TOP leem de um cache em memória, nunca consultam o
  banco na hora do request (PAPI pode chamar isso de qualquer thread).

## Dependências

- **AlkaCore** (hard dependency) — banco de dados (HikariCP/SQLite/MySQL) e
  sistema de GUI compartilhados. AlkaTime não abre conexão JDBC própria.
- **AlkaEconomy** (hard dependency) — moeda das recompensas de tempo.
- PlaceholderAPI, Citizens e DecentHolograms são soft-dependencies opcionais.

## Origem

Reconstruído do zero a partir de duas referências: o plugin antigo `KTempo`
(contagem de sessão + menu de recompensas em YAML puro) e uma descrição de um
plugin de terceiros ("yTempoOnline") usada só como inspiração de features —
nenhum dos dois foi copiado; tudo foi reescrito sobre a arquitetura do
AlkaCore (`BaseGui`, `AbstractRepository`, `AlkaScheduler`, MiniMessage).

## Débitos conhecidos

- Sem testes em servidor real ainda.
- `/tempo top` e o holograma do NPC limitam a exibição ao tamanho do menu/lista
  configurada — não há paginação além do primeiro "page" do TOP.
