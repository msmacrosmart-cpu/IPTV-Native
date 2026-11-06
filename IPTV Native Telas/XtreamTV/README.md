# Xtream Player — Android nativo (Kotlin + Jetpack Compose + Media3)

100% Kotlin/Compose, 100% paisagem (`sensorLandscape`), D-pad (Android TV / TV box) + toque.

## Telas
Login Xtream · Home · Filmes · Séries · TV ao vivo (3 colunas + preview ExoPlayer) · Favoritos · Configurações (conta, expiração, atualizador e leitor de EPG) · Player avançado.

## Rodar
1. Abra a pasta no Android Studio (Koala+ / JDK 17) e aguarde o Gradle Sync.
2. Run no emulador/dispositivo/TV. Na tela de login use `demo` como servidor (qualquer usuário/senha) para ver os dados de demonstração, ou seu servidor Xtream real (`http://host:porta`).

## Snapshots (PNG) sem emulador
`./gradlew testDebugUnitTest` → gera `app/snapshots/*.png` (Roborazzi + Robolectric).

## Player (VLC-like)
Velocidade 0.25–3x · faixas de áudio/legenda · proporção (Ajustar/Preencher/Zoom/16:9/4:3) · loop A-B · sleep timer · bloqueio de tela · gestos (duplo toque ±10s, arrastar dir. = volume, esq. = brilho) · info técnica · prev/next canal · D-pad (◄► ±10s, OK controles, CH+/CH−).

## Estrutura
data/ (modelos, API Xtream, EPG XMLTV, ViewModel, demo) · ui/theme · ui/components · ui/login · ui/home · ui/browse · ui/live · ui/settings · ui/player · ui/main.
