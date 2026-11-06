# IPTV Native — Android & Android TV (Kotlin + Jetpack Compose + Media3)

Player **Xtream Codes** nativo, 100% Kotlin, com Compose, Media3 ExoPlayer e ícone/logo próprios.
O APK gerado no GitHub Actions é instalável direto no celular, tablet ou TV Box.

---

## ✅ Correções desta versão (2.0.0)

Problemas relatados: o app abria com o ícone padrão do Android e as telas não funcionavam.

| Problema encontrado | Causa | Correção |
|---|---|---|
| Ícone genérico do Android | Manifest apontava para `@android:drawable/sym_def_app_icon` e o projeto **não tinha nenhum `mipmap/ic_launcher`** | Ícone adaptativo (`mipmap-anydpi-v26`) + PNGs em todas as densidades (48→192 px) + ícone monocromático (Android 13+) + banner de TV. Logo em vetor (`ic_logo_mark`) usada no app |
| App fechava / abria sem nada | Manifest declarava `.IptvApplication`, mas a classe Kotlin era `IPTVApplication` → `ClassNotFoundException` na inicialização | Manifest e classe alinhados (`IPTVApplication`), com `SessionManager` + `XtreamRepository` criados no `Application` |
| Projeto não compilava | `XtreamApi.kt` usava modelos inexistentes (`StreamItem`, `VodItem`) e faltava `data/preferences/SessionManager.kt` | Arquivo removido, modelos reais criados (`LiveChannel`, `VodItem`, `SeriesItem`…) e `SessionManager` implementado |
| Login sem função | Falha de rede caía em dados de demonstração falsos | Nenhum mock: erros reais, mensagens claras, URL/campos validados e sessão persistida |
| Tela preta ao abrir canal | Streams apontavam para domínios de demonstração (`demo.xtream.local`) | URLs reais do painel (`/live/`, `/movie/`, `/series/`), com fallback automático MPEG-TS ↔ HLS e mensagem de erro com botão de nova tentativa |
| Grids vazios | Sem parse tolerante do JSON do Xtream | Parser defensivo (`XtreamParser`): aceita número ou string, `{}`/`[]`, EPG em base64, séries por temporada |
| Fundo “quebrado” | `bg_app_main.jpg` era um **print da interface** usado como papel de parede | Fundo procedural em Canvas (glassmorphism + topografia), sem imagens esticadas |

---

## 🔐 Login e sessão

- `player_api.php` sem `action` para autenticar (`username`/`password`).
- URL aceita em qualquer formato:
  - `http://painel.com:8080`
  - `painel.com:8080`
  - `http://painel.com:8080/player_api.php`
  - `http://painel.com/get.php?username=USER&password=PASS&type=m3u_plus`
  - `https://painel.com/subpasta/` (painéis com sub-path)
  - link de canal copiado (`/live/user/pass/123.ts`) → a raiz do painel é recuperada
- Credenciais salvas em `SharedPreferences` privado, com a **senha cifrada em AES/GCM via Android Keystore** (fallback seguro se o Keystore do aparelho estiver quebrado).
- Sessão restaurada na abertura do app: entra direto no catálogo e valida a conta em segundo plano (mostra aviso se estiver offline, em vez de tela vazia).

## 📡 API Xtream Codes (player_api.php)

Retrofit + OkHttp (`ApiClient`) com user-agent próprio, timeouts, redirects HTTP→HTTPS e log apenas em debug.
Endpoints implementados: `get_live_categories`, `get_live_streams`, `get_short_epg`, `get_vod_categories`,
`get_vod_streams`, `get_vod_info`, `get_series_categories`, `get_series`, `get_series_info`.

Todas as respostas passam pelo `XtreamParser`, tolerante às inconsistências dos painéis reais.
Erros de HTTP/JSON/rede viram mensagens em português na própria tela (com “Tentar novamente”).

## ▶️ Player Media3 ExoPlayer

- URLs do Xtream:
  - Live: `{base}live/{user}/{pass}/{stream_id}.{ts|m3u8}`
  - Filme: `{base}movie/{user}/{pass}/{vod_id}.{extensão}`
  - Episódio: `{base}series/{user}/{pass}/{episode_id}.{extensão}`
- Preview na 3ª coluna da TV ao Vivo e player em tela cheia.
- **Landscape travado** (`sensorLandscape` no manifest + `requestedOrientation` no player), modo imersivo (system bars ocultas) e `FLAG_KEEP_SCREEN_ON`.
- Buffer configurável (5–60 s), decoder Hardware/Auto/Compatibilidade e escolha do formato TS/HLS.
- **Fallback automático**: se o stream falhar, tenta o formato alternativo antes de exibir erro.
- Zapping (canal anterior/próximo) dentro do player e barra de progresso para filmes/episódios.
- Player pausa no `ON_STOP` e retoma no `ON_START`; `release()` garantido ao sair da tela.

## 📺 Telas

- **Login**: campos de 56dp com o mesmo raio do botão, medidor de erro, dica de formato de URL.
- **Início**: saudação da conta real, hero do primeiro título, prateleiras de canais, filmes e séries.
- **TV ao Vivo**: 3 colunas — categorias + busca / lista de canais (logo, nº, arquivo) / preview + EPG (agora, progresso, sinopse, a seguir).
- **Filmes**: 2 colunas com grid de pôsteres (nota, extensão), busca e filtro por categoria.
- **Séries**: 2 colunas + detalhe com temporadas, episódios (título, sinopse, duração) e play direto.
- **Configurações**: dados reais da conta (status, expiração, conexões, servidor), formato de stream, buffer, decoder, EPG, teste de latência, limpeza de cache e logout.

## 🎨 Identidade visual

Marca: disco coral em gradiente + play branco, sobre fundo grafite.
Arquivos do ícone:

```
res/drawable/ic_logo_mark.xml              → marca usada no app (login, topo, splash)
res/drawable/ic_launcher_background.xml    → fundo do ícone adaptativo
res/drawable/ic_launcher_monochrome.xml    → ícone temático (Android 13+)
res/drawable/tv_banner.xml                 → banner do Android TV
res/mipmap-anydpi-v26/ic_launcher*.xml     → ícone adaptativo (API 26+)
res/mipmap-*/ic_launcher*_round.png        → PNGs legados (API 24–25 e launchers antigos)
tools/generate_icons.py                    → gerador dos PNGs (somente stdlib)
```

Para regerar os PNGs após qualquer ajuste da marca:

```bash
python3 tools/generate_icons.py
```

## 🗂 Estrutura

```
com.example.iptv
├── IPTVApplication.kt              # Application: SessionManager + repositório
├── MainActivity.kt                 # Activity única: splash, login, shell, player fullscreen
├── data
│   ├── model
│   │   ├── ApiResult.kt            # ApiResult + LoadState
│   │   └── XtreamModels.kt         # Conta, canais, EPG, VOD, séries, PlaybackRequest
│   ├── network
│   │   ├── ApiClient.kt            # OkHttp + Retrofit
│   │   ├── PanelUrl.kt             # Normalização de URL/credenciais do painel
│   │   ├── XtreamApiService.kt     # Endpoints player_api.php
│   │   └── XtreamParser.kt         # JSON tolerante + EPG base64
│   ├── preferences
│   │   └── SessionManager.kt       # SharedPreferences + AES/GCM (Keystore)
│   └── repository
│       └── XtreamRepository.kt     # Login, catálogo, URLs de stream, ping
├── player
│   ├── PlayerFactory.kt            # ExoPlayer + buffer + decoder + mime
│   ├── PreviewPlayer.kt            # Preview (3ª coluna)
│   └── PlayerScreen.kt             # Player tela cheia
├── ui
│   ├── components                  # GlassCard, fundo procedural, nav rail, componentes comuns
│   ├── screens                     # Login, Início, TV, Filmes, Séries, Configurações
│   └── theme                       # Cores e tema Material 3
├── util                            # FormatUtils, CacheUtils
└── viewmodel                       # AuthViewModel, MainTvViewModel
```

## 🛠 Build

Requisitos: JDK 17, Android SDK 35.

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # assinado com a keystore de debug (instalável)
```

No GitHub Actions (`.github/workflows/build.yml`) os dois APKs são compilados a cada push e ficam
disponíveis nos artefatos **IPTV-Native-debug** e **IPTV-Native-release**.

## 📱 Uso

1. Instale o APK.
2. Informe a URL do painel (ex.: `http://servidor.com:8080`), usuário e senha.
3. O catálogo é carregado automaticamente. Na TV ao Vivo, toque em um canal para ver no preview e
   use o ícone de tela cheia (ou o botão **Assistir**) para abrir o player.
4. Em **Configurações**, se algum canal não abrir, troque o formato para **HLS (m3u8)**.
