# IPTV Native

Aplicativo Android em Kotlin/Jetpack Compose para Android TV, celulares e tablets, em orientação horizontal. Use somente playlists e transmissões para as quais você tem autorização: o aplicativo não inclui canais nem credenciais.

## O que há na edição 2.5.0

- Login Xtream Codes, sessão salva e perfis locais por servidor/usuário, com troca também sem conexão quando há dados previamente salvos. Favoritos, histórico, categorias bloqueadas e preferências ficam separados por perfil. É possível remover um perfil inativo ou sair do ativo.
- Tela de carregamento com a marca e indicadores das etapas de leitura do cache e download de categorias, canais, filmes e séries. Em uma primeira sessão, a navegação aguarda a primeira atualização; com cache, ele é mostrado enquanto a atualização ocorre em segundo plano. Download de EPG pode continuar depois do catálogo.
- Home com hero horizontal de até seis filmes das categorias de lançamentos, navegável com D-pad e ações distintas para assistir/detalhes. Os Top 10 usam a nota do provedor; linhas sem conteúdo ficam ocultas.
- TV ao vivo com categorias, favoritos, pesquisa e EPG. **Mover o foco não inicia reprodução**: pressione OK/toque em um canal para iniciar sua prévia e novamente para abrir o player. A prévia não é retomada automaticamente depois de trocar de perfil ou recriar a tela.
- Catálogos de filmes e séries, temporadas, episódios, favoritos e retomada. A opção de próximo episódio toca a sequência automaticamente quando o episódio termina no player interno.
- Multitela com um a quatro players internos independentes e seleção do áudio de um deles. A disponibilidade depende do número de conexões do plano, codecs e capacidade do dispositivo.
- Proteção por PIN local para ocultar categorias de canais, filmes e séries da Home, buscas, TV e Multitela. É uma barreira de interface para uso doméstico, não substitui controles do sistema ou do provedor.

### Configurações disponíveis

- Conta e perfis; teste de tempo de resposta do painel, que inclui a consulta de categorias (não é medição de velocidade de internet).
- Reprodução: TS ou HLS para TV, decoder automático/software/hardware, buffer, user agent, legendas e tamanho, próximo episódio e escolha de player interno/externo separadamente para TV, filmes e séries. Alterações de reprodução valem ao criar o próximo player. Um player externo recebe a URL da mídia, que pode conter usuário e senha do provedor.
- EPG: XMLTV do painel ou URL própria, atualização manual/automática, intervalo, ajuste de fuso de −12 a +12 horas, relógio 12/24 h, exibição nas linhas, filtro de canais com guia disponível e lista de programação resumida ou completa. O Android pode adiar atualizações em segundo plano; o filtro utiliza apenas dados de EPG já carregados.
- Controle parental: criar/alterar PIN, desbloquear/bloquear e selecionar categorias. O PIN é derivado com PBKDF2 e salt; sem o PIN atual não há recuperação pela interface.
- Multitela: número de quadros. Armazenamento: limpar manualmente o cache de catálogo e EPG do perfil atual e o cache de imagens compartilhado, sem apagar favoritos ou histórico. É necessário acesso ao provedor para baixar o conteúdo novamente.

O APK de referência foi inspecionado estaticamente para identificar opções e layouts, mas não há alegação de paridade completa: gravação, VPN embutida, picture-in-picture, inicialização com o dispositivo, seleção de idioma e teste de velocidade em Mbps não estão implementados. A inicialização automática de canais foi intencionalmente omitida para preservar a exigência de reprodução somente por ação explícita. Alguns rótulos do APK de referência não demonstram, por si só, o comportamento de execução.

## Conexão e privacidade

Informe URL do servidor, usuário e senha Xtream Codes. O app aceita HTTP para compatibilidade, mas HTTP pode expor credenciais e conteúdo na rede; prefira HTTPS. A senha e a URL XMLTV própria são criptografadas com Android Keystore quando disponível. Se o Keystore falhar, usa-se somente uma ofuscação reversível, que **não é criptografia**. Servidor, usuário e dados locais dos perfis não são secretos. Backups do Android estão desativados.

O cache mantém o catálogo por três dias antes de tentar atualizá-lo; EPG segue o intervalo configurado e as restrições do Android. Em modo offline, só é possível navegar pelo conteúdo já salvo e reproduzir mídias cujo endereço ainda esteja acessível. Use o botão de demonstração para explorar uma interface sem conta: ele não fornece streams reais.

## Desenvolvimento e testes

Requisitos: JDK 17 e Android SDK 35. A versão mínima é Android 7.0 (API 24).

```sh
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug :app:assembleRelease
./gradlew --no-daemon :app:connectedDebugAndroidTest
```

O workflow `.github/workflows/build.yml` compila, roda testes unitários e testes de interface em emuladores Android TV e Pixel e publica capturas/APKs. Os testes de integração usam um provedor Xtream simulado e vídeo sintético; eles não substituem testes em hardware físico, com provedores reais, DRM ou codecs específicos.

A configuração `release` deste repositório usa a **chave de debug** para gerar um APK instalável no CI. Não é uma assinatura de produção: configure sua própria chave antes de distribuir. Android não instala uma atualização sobre o mesmo pacote assinado por outra chave; nesse caso, desinstalar a versão anterior pode apagar seus dados locais.
