# AlkaTime

> Tempo é recompensa. Contagem de tempo online, missões e ranking pra rede Alka*.

![Java](https://img.shields.io/badge/Java-21-orange)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.8-green)
![Version](https://img.shields.io/badge/Version-1.0.2-blue)
![License](https://img.shields.io/badge/License-Proprietary-red)

---

## 📋 Sobre o Projeto

O **AlkaTime** acompanha o tempo que cada jogador passa online e transforma
isso em recompensa: missões de tempo pagam moedas e comandos configuráveis, um
ranking mostra quem mais joga na rede, e tudo isso com uma contagem em tempo
real que nunca depende de consultas repetidas ao banco.

## ✨ Funcionalidades Principais

- ⏱️ **Contador em tempo real** — o tempo online é calculado ao vivo, sem
  travar o servidor com consultas constantes ao banco.
- 🎁 **Missões de recompensa infinitas** — configure quantas quiser, cada uma
  pagando moeda e/ou comandos ao ser atingida.
- 🖼️ **Menu completo** (`/tempo`) — tempo jogado, grade de missões e acesso
  direto ao ranking.
- 🏆 **Ranking de tempo online** (`/tempo top`) — paginado, considera até
  jogadores offline.
- 🧍 **NPC configurável** via Citizens, com holograma opcional mostrando o
  TOP 1 atualizado.
- 💾 **Autosave periódico** — protegido contra queda do servidor.
- 🔤 **PlaceholderAPI completo** — tempo formatado, horas, e placeholders de
  ranking prontos pra scoreboard/chat.

## 🎮 Comandos

| Comando | Descrição | Permissão |
| --- | --- | --- |
| `/tempo` | Mostra seu tempo jogado e as missões disponíveis | `alkatime.usar` |
| `/tempo top` | Abre o ranking de tempo online | `alkatime.top` |
| `/tempo <jogador>` | Vê o tempo de outro jogador | `alkatime.look.others` |
| `/alkatime setnpc` \| `delnpc` | Define/remove o NPC de tempo online | `alkatime.admin` |
| `/alkatime reload` | Recarrega as configurações | `alkatime.admin` |
| `/alkatime set` \| `add` \| `remove` \| `reset` | Ajusta o tempo de um jogador manualmente | `alkatime.admin` |

## 🔗 Integrações

Construído sobre o **AlkaCore** (banco e GUI) e a **AlkaEconomy** (moeda
`ticks` das recompensas). Suporte opcional a **PlaceholderAPI**, **Citizens**
(NPC) e **DecentHolograms** (holograma do TOP 1). Expõe uma API pública
(`AlkaTimeAPI`) consumida pelo **AlkaRankUp** para ranks com requisito de
tempo online.

## 🔧 Tecnologias Utilizadas

- **Java 21** · **Paper API 1.21.8**
- **AlkaCore** (banco de dados e GUI compartilhados)
- **MiniMessage** para todas as mensagens

## ⚙️ Instalação

1. Baixe a versão mais recente do plugin.
2. Coloque o `.jar` na pasta `plugins/` do servidor.
3. Certifique-se de ter o **AlkaCore** e a **AlkaEconomy** instalados
   (dependências obrigatórias).
4. Reinicie o servidor e configure `plugins/AlkaTime/rewards.yml` com suas
   missões de recompensa.

## 🔐 Permissões

- `alkatime.usar` — usar `/tempo` (padrão: `true`)
- `alkatime.top` — usar `/tempo top` (padrão: `true`)
- `alkatime.look.others` — ver o tempo de outro jogador (padrão: `op`)
- `alkatime.veroff` — ver tempo de jogadores offline (padrão: `op`)
- `alkatime.bypass` — não acumula tempo online (padrão: `false`)
- `alkatime.admin` — acesso administrativo completo (padrão: `op`)

## 📝 Licença

> ⚠️ **Projeto proprietário da AlkaStudio.**
>
> Código fonte destinado exclusivamente ao uso interno da rede `Alka*`.
> Reprodução, distribuição ou uso não autorizado não são permitidos.

## 🎯 Créditos

- **Desenvolvido por**: MestreDEV — AlkaStudio
- **Parte do ecossistema**: `Alka*`

---

<div align="center">

**Desenvolvido com ❤️ pela AlkaStudio**

[![AlkaStudio](https://img.shields.io/badge/AlkaStudio-JLob0-blue)](https://github.com/JLob0)

</div>
