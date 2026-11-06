# IPTV Native

Aplicativo Android/Android TV em Kotlin, Jetpack Compose e Media3 para contas Xtream Codes. O aplicativo mantém o identificador `com.example.iptv` e pode reutilizar a sessão salva por versões anteriores.

## Interface e funcionamento

As referências completas estão em `IPTV Native Telas/XtreamTV/`. O aplicativo real usa essas telas em `app/src/main/java/com/xtream/player/`; a pasta de referência não é um módulo do build principal.

- Login, Início, Filmes, Séries, TV ao vivo, Favoritos, Configurações e player usam o mesmo repositório e a mesma sessão. Na busca do catálogo, os filtros de categoria e texto são locais ao conteúdo carregado.
- Em Séries, selecione um episódio para reproduzir. Um toque longo ou OK prolongado marca filmes, séries e canais como favoritos. Favoritos e posições de reprodução são guardados por conta no dispositivo.
- A TV ao vivo inclui programação, prévia inicialmente sem som, tela cheia e troca de canais. A prévia é liberada ao entrar no player. O player tenta o formato alternativo de transmissão ao ocorrer um erro, quando disponível.
- O EPG usa `xmltv.php` do painel, aceita XML e XML compactado com gzip e mantém uma janela limitada de programação em memória. Se o XMLTV não contiver o canal selecionado, consulta `get_short_epg`. A tela de Configurações permite atualização manual; a atualização automática é verificada ao abrir o aplicativo depois de 12 horas, não por uma tarefa periódica em segundo plano. URLs XMLTV personalizadas salvas anteriormente continuam sendo usadas, mas a interface fornecida não possui um campo para editá-las.
- As credenciais são armazenadas pela sessão existente. O Android Keystore é usado quando disponível; aparelhos incompatíveis usam o fallback de ofuscação já existente. Ao sair, a sessão e o cache XMLTV são removidos; favoritos e progresso permanecem associados à conta no dispositivo.
- Para visualizar os dados de demonstração, digite `demo` como servidor e preencha usuário e senha. O modo de demonstração é temporário; os vídeos dependem dos servidores de exemplo.

Uma conta válida e um serviço que ofereça os conteúdos e a programação são necessários para uso real. A disponibilidade e os formatos dependem do provedor. Prefira HTTPS quando disponível: HTTP transmite dados sem criptografia.

## Build e testes

Requisitos: JDK 17, Android SDK 35 e acesso aos repositórios configurados no Gradle.

```sh
./gradlew testDebugUnitTest
# Com um emulador ou dispositivo conectado:
./gradlew connectedDebugAndroidTest
./gradlew assembleDebug
./gradlew assembleRelease
```

O workflow de CI verifica o build e os testes e disponibiliza os APKs como artifacts. O `release` atual é assinado com uma **chave de debug**, apenas para testes. Para distribuição definitiva ou atualização de uma instalação assinada com outra chave, configure a chave de produção correspondente.
