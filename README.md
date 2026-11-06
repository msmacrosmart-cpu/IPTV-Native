# SunsetTV — Live TV + Configurações (Kotlin / Jetpack Compose)

Pacote base: `com.sunsettv.app` (troque por o do seu projeto com "Refactor > Rename").

## O que substituir / copiar
- `AndroidManifest.xml` → mescle com o seu (todas as Activities com `sensorLandscape`).
- `app/build.gradle.kts` → confira as dependências (Media3 1.4.1, Coil 2.7, OkHttp 4.12, Compose BOM 2024.10).
- `SunsetApp.kt` → `ServiceLocator.init(this)` (registre em `android:name`).
- `core/` , `domain/`, `data/` → camada de dados (Xtream API, XMLTV, cache de EPG).
- `ui/theme`, `ui/components` → tema e componentes (glass, foco neon, botões).
- `ui/live` → `LiveTvScreen(onOpenPlayer = { canal -> ... })`
- `ui/settings` → `SettingsScreen(onLogout = { ... })`

## Integração
```kotlin
// dentro do conteúdo ao lado do seu menu lateral de ícones:
when (destino) {
    Destino.LiveTv   -> LiveTvScreen(onOpenPlayer = { ch -> abrirPlayer(ch.streamUrl, ch.name) })
    Destino.Settings -> SettingsScreen(onLogout = { voltarParaLogin() })
}
```
- Salve as credenciais no login com `ServiceLocator.session.save(XtreamCredentials(servidor, usuario, senha))`
  (ou ajuste `SessionStore.get()` para ler de onde você já guarda).
