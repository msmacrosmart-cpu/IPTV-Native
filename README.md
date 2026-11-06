# IPTV Native Android & Android TV (100% Kotlin + Jetpack Compose)

Projeto 100% nativo em **Kotlin** e **Jetpack Compose / Android TV Leanback**, pronto para ser aberto diretamente no **Android Studio** e compilado em APK via Gradle.

---

## 🎨 Características Visuais & Design System

1. **Plano de Fundo Topográfico Damasco**: Textura preta e cinza com linhas topográficas em damasco/bronze renderizada via Canvas vetorial de alta definição e acelerada por hardware.
2. **Glassmorphism Avançado**: Superfícies escuras translúcidas, bordas suaves e iluminadas com gradientes sutis e elevação de foco D-pad.
3. **Sem Riscos ou Divisores Neon**: Transição totalmente limpa entre a seção Hero e as prateleiras de conteúdo.
4. **Botão de Login Ajustado**: O botão principal da tela de Login possui exatamente o mesmo tamanho (largura e altura de 56dp) e raio de canto das caixas de texto.

---

## 📺 Estrutura das Telas Nativas

- **Tela de Login (`LoginScreen.kt`)**: Autenticação com Xtream Codes oficial (`player_api.php`), validação de campos vazios e botão no tamanho exato das caixas de texto.
- **TV ao Vivo em 3 Colunas (`LiveTvScreen.kt`)**: 
  - Coluna 1: Categorias de canais, chip 'Selecionar Todos' e busca em tempo real.
  - Coluna 2: Canais da categoria com logos, nome e EPG atual com barra de progresso.
  - Coluna 3: Pré-visualização com **Media3 ExoPlayer** (`PlayerView`) + card completo de informações do EPG.
- **Filmes & Séries em 2 Colunas (`VodScreen.kt` & `SeriesScreen.kt`)**:
  - Coluna 1: Categorias de Filmes/Séries + busca funcional.
  - Coluna 2: Grid de pôsteres com notas ★, ano, metadados e foco D-pad animado.
- **Sistema de Configurações (`SettingsScreen.kt`)**:
  - Informações da conta ativa e data de expiração calculada.
  - Gerenciador e atualizador manual/automático de EPG.
  - Seleção de decoder de vídeo (Hardware/Software/Auto) e tamanho de buffer.
  - Teste de ping/latência e limpeza de cache.
- **Player em Tela Cheia (`PlayerScreen.kt`)**:
  - Reprodução HLS / MPEG-TS / MP4 com Media3 ExoPlayer, badge LIVE, play/pause circular com glow coral e barra de tempo.

---

## 📁 Estrutura de Pacotes

```text
com.example.iptv
├── data
│   ├── model
│   │   └── XtreamModels.kt          # DTOs de Auth, Live, VOD, Séries e EPG
│   ├── network
│   │   ├── ApiClient.kt             # Factory OkHttp/Retrofit com serialização JSON
│   │   └── XtreamApiService.kt      # Endpoints player_api.php do Xtream Codes
│   └── repository
│       └── XtreamRepository.kt      # Repositório com cache, fallback e URLs de stream
├── player
│   └── PlayerScreen.kt              # Player fullscreen Media3 ExoPlayer
├── ui
│   ├── components
│   │   ├── GlassCard.kt             # Container glassmorphic com foco D-pad
│   │   ├── TopographicBackground.kt # Fundo damasco vetorial
│   │   └── TvNavigationRail.kt      # Barra lateral para Android TV
│   ├── screens
│   │   ├── DashboardScreen.kt       # Home com Hero e prateleiras
│   │   ├── LiveTvScreen.kt          # Layout 3 colunas (IPTV Smarters)
│   │   ├── LoginScreen.kt           # Login com botão tamanho idêntico aos inputs
│   │   ├── SeriesScreen.kt          # Séries em 2 colunas
│   │   ├── SettingsScreen.kt        # Configurações IPTV Smarters
│   │   └── VodScreen.kt             # Filmes em 2 colunas
│   └── theme
│       ├── Color.kt                 # Cores Damasco, Coral e Glass
│       └── Theme.kt                 # Material3 Dark Theme
├── viewmodel
│   ├── AuthViewModel.kt             # Lógica de login e validação
│   └── MainTvViewModel.kt           # Estado reativo das 5 telas
├── IptvApplication.kt               # Application class
└── MainActivity.kt                  # Entry point e controle de navegação
```

---

## 🚀 Como Abrir e Compilar no Android Studio

1. Abra o **Android Studio** (Koala, Ladybug ou superior).
2. Selecione **File > Open...** e aponte para a pasta `iptv-native-android`.
3. Aguarde o **Gradle Sync** automático sincronizar as dependências do Media3 e Jetpack Compose.
4. Para gerar o APK imediatamente via terminal:
   ```bash
   ./gradlew assembleDebug
   ```
   O APK gerado estará em: `app/build/outputs/apk/debug/app-debug.apk`.
5. Para instalar na sua Android TV ou dispositivo Android:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
