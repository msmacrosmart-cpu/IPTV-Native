# IPTV Native

Aplicativo Android nativo em Kotlin/Jetpack Compose para Android TV, celulares e tablets, em orientação `sensorLandscape`. Use somente serviços e transmissões para os quais você tem autorização: o aplicativo não inclui canais nem credenciais.

## Edição 2.7.1

- A arte horizontal fornecida aparece somente no carregamento. A arte quadrada é usada nos ícones adaptativos e clássicos do Android, e a imagem 320×180 no banner do Android TV.
- Navegação lateral com quatro ícones em neon, inspirados na imagem fornecida: Home, TV, Filmes e Séries. A engrenagem do cabeçalho mantém o acesso às configurações; os ícones ao lado abrem status da conta e troca de perfis. A opção Multitela foi retirada.
- Tela de login com botão Entrar do tamanho dos campos e perfis locais por servidor/usuário. O wallpaper fornecido é usado no login e no restante do aplicativo, com o PNG original preservado, sem recorte ou escurecimento global. O formulário evita cobrir o contato/QR em telas horizontais estreitas.
- Home com até oito filmes com backdrop horizontal validado da pasta Lançamentos do provedor (mesmo com emojis no nome). A busca continua em lotes pela pasta até encontrar oito banners ou esgotar os filmes; não mistura pastas infantis ou temáticas, botões Assistir/Detalhes e navegação por D-pad/teclado. Com entrada por controle ou teclado, o banner não troca automaticamente. Imagens verticais nunca são usadas como banner: se não houver arte paisagem válida, o filme não entra no Hero.
- Prateleiras de conteúdo da Home rolam horizontalmente e não limitam a biblioteca aos primeiros itens. As abas de Filmes e Séries continuam usando **grades verticais** com rolagem contínua, por toque ou D-pad. Em cada pasta ou busca é possível ordenar de A a Z, por últimos adicionados ou por mais votados, alternando a direção entre crescente e decrescente.
- Ao voltar dos detalhes de um filme ou série, o aplicativo mantém a pasta, a pesquisa, a ordenação e a posição da grade; no controle, o foco retorna ao item. Cada aba e perfil mantém seu próprio estado de navegação durante a sessão.
- “Mais votados” usa a **nota informada pelo provedor**, não uma contagem real de votos. “Últimos adicionados” usa a data enviada pelo painel; para séries sem data de inclusão, usa a última atualização informada. Títulos sem data ou nota ficam por último nas duas direções.
- Detalhes exibem o banner na largura disponível, sinopse, uma única linha horizontal de elenco com fotos carregadas, navegável com o controle (atores sem foto válida são omitidos; sem fotos, a seção é ocultada) e, quando disponível, trailer aberto no YouTube ou aplicativo compatível. O TMDB fornece automaticamente dados e fotos do elenco quando a versão foi compilada com a chave; dados do painel são preservados como alternativa.
- TV ao vivo com categorias, favoritos, pesquisa e EPG. Mover o foco não inicia reprodução: pressione OK/toque em um canal para iniciar a prévia e novamente para abrir o player. A programação e a disponibilidade dependem do provedor.
- Sessão, catálogo, favoritos, histórico e preferências são mantidos por perfil. Uma reabertura usa o catálogo salvo antes de validar a conta ou atualizar o guia; com a rede indisponível, ainda é possível navegar no conteúdo já armazenado. Vídeos só reproduzem se o endereço continuar acessível.
- Proteção por PIN local para ocultar categorias na Home, buscas e TV. É uma barreira de interface doméstica, não substitui controles do sistema ou do provedor.

### Configurações

Cada atalho abre somente seu painel, com retorno ao menu e o mesmo estilo glassmorphism. O painel escuro usa atalhos e nomenclaturas inspirados nas opções identificadas no APK de referência, sem alegar paridade integral:

- Conta e perfis; teste do tempo de resposta do painel (não é medição de velocidade de internet).
- Formato TS/HLS, decoder, buffer, user agent, legendas, próximo episódio e escolha de player interno/externo por tipo de conteúdo. Um player externo recebe a URL da mídia, que pode conter as credenciais do provedor.
- EPG: XMLTV do painel ou URL própria, atualização manual/automática, intervalo (padrão **1 dia**), ajuste de fuso, relógio 12/24 h e exibição/filtragem na lista de canais.
- Automação: catálogo atualizado em segundo plano a cada **3 dias por padrão**, com opções de 1, 2 ou 3 dias. O Android pode adiar tarefas devido a economia de bateria, conectividade ou outras restrições.
- Controle parental e limpeza manual do cache do perfil, sem apagar favoritos ou histórico. É necessário acesso ao provedor para baixar novamente o conteúdo removido.
- VPN abre as configurações do Android, e o teste de velocidade abre um serviço externo no navegador; o aplicativo não inclui uma VPN própria nem mede Mbps internamente. A identificação do dispositivo é automática. Gravação, picture-in-picture e inicialização automática não estão implementados.

## TMDB, conexão e privacidade

Não há campo de chave do TMDB nas configurações. A integração usa uma chave de API v3 fornecida na compilação; se ela estiver ausente ou o TMDB estiver indisponível, o app continua usando os dados do provedor. Dependendo dos dados disponíveis, elenco, fotos e trailer podem não existir para determinado título. Para o hero são consultados os filmes de Lançamentos em lotes, até obter oito banners válidos ou esgotar a pasta; nos detalhes são enviados ao TMDB o título e, quando disponível, o ano ou o ID do provedor. As credenciais Xtream não são enviadas ao TMDB.

This product uses the TMDB API but is not endorsed or certified by TMDB. Mais informações em [LICENSES/TMDB.md](LICENSES/TMDB.md).

A chave incorporada ao APK **pode ser extraída por terceiros**; evite usá-la como segredo de alta segurança e aplique restrições compatíveis com seu uso. Não inclua tokens no repositório. Para compilar com TMDB, configure a variável de ambiente `TMDB_API_KEY` ou a propriedade Gradle de mesmo nome; no GitHub Actions, use o secret `TMDB_API_KEY`. Uma compilação sem chave permanece funcional, mas sem consultas ao TMDB.

Informe URL do servidor, usuário e senha Xtream Codes. HTTP pode expor credenciais e conteúdo na rede; prefira HTTPS. A senha e a URL XMLTV própria são criptografadas com Android Keystore quando disponível. Se o Keystore falhar, usa-se somente uma ofuscação reversível, que **não é criptografia**. Servidor, usuário e dados locais dos perfis não são secretos. Backups do Android estão desativados.

## Desenvolvimento e testes

Requisitos: JDK 17 e Android SDK 35. A versão mínima é Android 7.0 (API 24).

```sh
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug :app:assembleRelease
./gradlew --no-daemon :app:connectedDebugAndroidTest
```

O workflow `.github/workflows/build.yml` compila e executa testes unitários e de interface em emuladores Android TV e Pixel. Os testes de integração usam um provedor Xtream simulado e vídeo sintético; não substituem testes em aparelhos físicos, com provedores reais, DRM ou codecs específicos.

A configuração `release` usa a **chave de debug** para gerar um APK instalável no CI. Não é uma assinatura de produção: configure sua própria chave antes de distribuir. O Android não instala uma atualização sobre o mesmo pacote assinado por outra chave; nesse caso, desinstalar a versão anterior pode apagar os dados locais.
