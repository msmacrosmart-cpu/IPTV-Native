# IPTV Native

Aplicativo Android e Android TV em Kotlin/Jetpack Compose para contas Xtream Codes. O projeto mantém o identificador `com.example.iptv`, a sessão existente e as seções Início, TV ao Vivo, Filmes, Séries e Configurações.

## Telas integradas

As referências em `IPTV Native Telas/` correspondem às telas de **TV ao Vivo** e **Configurações**. As versões integradas estão em `app/src/main/java/com/example/iptv/ui/screens/` e usam o mesmo repositório e a mesma sessão do restante do aplicativo; a pasta de referência não é um segundo módulo do Gradle.

- TV ao Vivo: categorias e busca, foco pelo controle remoto, prévia, reprodução em tela cheia, troca de canais e programação.
- Configurações: conta e plano, atualização manual/automática do EPG, fonte XMLTV opcional, sessão e os controles existentes de reprodução, rede e cache.
- O EPG usa `xmltv.php` do painel (ou uma URL HTTP(S) personalizada), aceita XML e XML compactado com gzip e mantém apenas uma janela de programação em memória. Quando o XMLTV não contém o canal, o app consulta `get_short_epg` do painel. A atualização automática é verificada ao abrir o app depois de 12 horas; não é uma tarefa periódica em segundo plano.
- As credenciais continuam no armazenamento de sessão existente; não são duplicadas para as novas telas. A saída limpa o guia em cache e a sessão. O armazenamento de senha usa Android Keystore quando disponível, com o fallback já existente para aparelhos incompatíveis.

É necessária uma conta válida e um serviço que ofereça os conteúdos e a programação. A disponibilidade e o formato dos dados dependem do provedor.

## Build e testes

Requisitos: JDK 17, Android SDK 35 e acesso aos repositórios configurados no Gradle.

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
```

O APK de debug fica em `app/build/outputs/apk/debug/`. O workflow de CI também disponibiliza os APKs como artifacts. A configuração `release` atual usa a chave de **debug**, apenas para testes: para distribuir uma versão definitiva ou atualizar uma instalação assinada com outra chave, configure a assinatura de produção correspondente.
