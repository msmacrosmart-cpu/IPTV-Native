# Mapeamento da referência IPTVnator

Referência analisada: [4gray/iptvnator](https://github.com/4gray/iptvnator), commit `d50f3ea25a47e599b6ccd2d3e11bb562a1c265d8`. O projeto de origem é web/desktop; esta implementação é Kotlin/Jetpack Compose, não incorpora um navegador nem o código Angular.

| Origem | Adaptação nativa |
| --- | --- |
| `apps/web/src/_theme-palettes.scss` e `styles.scss` | `ui/theme/Theme.kt`: fundo `#161a22`, rail `#111318`, painel `#1e2330`, texto `#d8dce8`, realce `#78adff`, anel de foco `#8cbaff`, DM Sans. |
| `libs/workspace/shell/feature/.../workspace-shell-rail*.scss` e header | `ui/components/Chrome.kt`: rail de ícones 44–48 dp, cantos de 14 dp e cabeçalho de 56 dp. Em largura compacta o rail passa a ser horizontal, como no breakpoint da referência. Somente as seis opções solicitadas são expostas. |
| `libs/workspace/dashboard/feature/src/lib/rails/dashboard-hero.component.{html,scss}` | `ui/home/HomeScreen.kt`: imagem de fundo em tela larga, degradês de legibilidade, informações, ação, indicadores e rotação pausada ao receber foco; sequência de cards 2:3 e canais favoritos. |
| `libs/workspace/dashboard/feature/src/lib/rails/_dashboard-rail-geometry.scss` e `libs/ui/styles/_content-grid.scss` | Cards com cantos de 10–12 dp, arte 2:3, título abaixo, indicador de progresso, avaliação e espaçamento de 14–16 dp; grade adaptativa e paginação em `ui/browse/BrowseScreen.kt`. |
| `libs/ui/styles/_portal-sidebar.scss` e `unified-live-tab.component.{html,scss}` | Categorias, lista de canais, seleção azul, prévia Media3 e programação em `ui/live/LiveScreen.kt`. Em telas estreitas categorias e prévia se reorganizam sem encolher a lista até ficar inutilizável. |
| Telas de detalhes VOD/séries | `ui/detail/DetailScreen.kt`: poster e backdrop com scrim, metadados, reprodução/favorito e temporadas/episódios do painel. |
| Configurações | `ui/settings/SettingsScreen.kt`: painéis escuros, campos nativos e ações para as credenciais Xtream, sincronização do catálogo e EPG. |
| Multitela | Não há seção equivalente no web de referência. `ui/multi/MultiScreen.kt` usa os mesmos tokens e até quatro instâncias independentes de Media3, com um só áudio por vez; é uma adaptação nativa solicitada, não uma cópia de uma tela inexistente. |

As dimensões são convertidas de px da web para dp como ponto de partida; a disposição muda para telas e densidades diferentes. Cartazes/backdrops reais vêm do provedor. O modo `demo` usa uma amostra compactada da arte ficcional do mock de divulgação da referência, identificada como demonstração.

A referência é distribuída sob MIT. Os avisos de copyright/licença estão em `LICENSES/` e também no aplicativo. A marca e o logotipo IPTVnator não foram reutilizados; o aplicativo mantém seu nome e ícone próprios.
