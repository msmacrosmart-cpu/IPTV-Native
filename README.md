# IPTV Native

Aplicativo Android/Android TV nativo em Kotlin, Jetpack Compose e Media3 para contas Xtream Codes. A interface segue o design system escuro do [IPTVnator](https://github.com/4gray/iptvnator); não usa WebView. O identificador `com.example.iptv` e a sessão existente foram mantidos.

## Interface e funcionamento

O mapeamento dos componentes e estilos da referência está em [docs/IPTVNATOR_DESIGN_MAP.md](docs/IPTVNATOR_DESIGN_MAP.md). A navegação principal possui apenas:

- **Home:** hero rotativo com **Assistir** e **Detalhes**, continuar assistindo e favoritos de canais, filmes e séries. O aplicativo procura backdrops do provedor; quando não existem, mantém o cartaz nítido, sem ampliá-lo e desfocá-lo.
- **TV:** três colunas para categorias, canais e prévia/programação EPG. No toque, selecione um canal para a prévia e toque novamente ou use tela cheia; na TV, o foco atualiza a prévia e OK abre o player. Toque longo ou OK prolongado adiciona/remova um favorito; o ícone de estrela também funciona.
- **Filmes:** coluna de categorias pesquisáveis e grade paginada de cartazes compactos. Abra o cartaz para ver detalhes, favoritar ou reproduzir.
- **Séries:** coluna de categorias pesquisáveis, cartazes compactos, detalhes, temporadas e episódios do painel.
- **Multitela:** até quatro canais simultâneos, com áudio apenas na tela selecionada. Os players são liberados ao sair da tela. A quantidade efetiva depende do limite de conexões do provedor e dos decodificadores do aparelho.
- **Configurações:** editar URL, usuário e senha Xtream; atualizar catálogo e EPG; sair da conta.

A interface é nativa e funciona em **paisagem** no celular, TV, projetor e TV stick com Android/Android TV, com controles por toque e D-pad. Em dispositivos reconhecidos como Android TV, os controles ganham uma margem de segurança para telas com overscan; o vídeo continua em tela cheia. A imagem de fundo enviada pelo usuário é usada no login e nas telas do aplicativo; os cards mantêm o mesmo tamanho compacto na Home e nos catálogos. Campos usam o teclado nativo do dispositivo, não um teclado próprio. O player Media3 mantém troca de canal, legendas, faixas de áudio, velocidade e retomada. Favoritos e posições são armazenados por conta no dispositivo.

O EPG usa `xmltv.php` do painel, suporta XML e gzip com cache limitado e consulta `get_short_epg` quando necessário. A atualização automática é verificada ao abrir o aplicativo após 12 horas; também é possível atualizar manualmente. URLs XMLTV personalizadas previamente salvas continuam sendo usadas. Credenciais usam Android Keystore quando disponível; aparelhos incompatíveis usam o fallback de ofuscação existente. Prefira HTTPS, pois HTTP transmite dados sem criptografia.

Para visualizar a interface com conteúdo **fictício**, digite `demo` como servidor e preencha usuário e senha. Os cartazes de demonstração são locais; os vídeos de demonstração dependem de servidores externos. Uma conta válida é necessária para conteúdo real, cuja disponibilidade depende do provedor.

## Build e testes

Requisitos: JDK 17, Android SDK 35 e acesso aos repositórios configurados no Gradle.

```sh
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest # com emulador ou dispositivo
./gradlew assembleDebug
./gradlew assembleRelease
```

O CI testa login, catálogos paginados, detalhes de séries, EPG, favoritos, player, navegação D-pad, celular em paisagem, ação Assistir no hero e dois players simultâneos com um painel/streams simulados. Também executa navegação por controle e verificação de área segura em um emulador Android TV. A reprodução com um provedor real depende da conta, formatos, rede e aparelho do usuário.

O `release` atual é assinado com uma **chave de debug**, somente para testes. Para distribuir ou atualizar uma instalação assinada com outra chave, configure a chave de produção correspondente.

## Créditos

IPTVnator: copyright 2019–2026 4gray, licença MIT. Os dados e imagens locais são parte do mock de demonstração ficcional do projeto de origem. DM Sans está sob SIL Open Font License. Os avisos completos estão em [`LICENSES/`](LICENSES/) e em Configurações > Licenças de terceiros. O nome e o logotipo IPTVnator são marcas do respectivo proprietário; este aplicativo mantém sua identidade própria.
