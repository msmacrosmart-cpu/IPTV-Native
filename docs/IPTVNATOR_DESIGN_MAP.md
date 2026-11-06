# Mapeamento da referência IPTVnator

Referência analisada: [4gray/iptvnator](https://github.com/4gray/iptvnator), commit `d50f3ea25a47e599b6ccd2d3e11bb562a1c265d8`. O projeto de origem é web/desktop; esta implementação é Kotlin/Jetpack Compose, não incorpora um navegador nem o código Angular.

| Origem | Adaptação nativa |
| --- | --- |
| `apps/web/src/_theme-palettes.scss` e `styles.scss` | `ui/theme/Theme.kt`: fundo neutro `#111214`, painéis pretos translúcidos `#c90d0f12`, seleção cinza-escura translúcida, texto claro e anel de foco branco; fonte DM Sans. O azul da referência foi removido conforme a solicitação. |
| `libs/workspace/shell/feature/.../workspace-shell-rail*.scss` e header | `ui/components/Chrome.kt`: rail de ícones 44–48 dp, cantos de 14 dp e cabeçalho de 56 dp. O rail permanece lateral em paisagem e reduz a altura dos controles em telas baixas. Somente as seis opções solicitadas são expostas. |
| `libs/workspace/dashboard/feature/src/lib/rails/dashboard-hero.component.{html,scss}` | `ui/home/HomeScreen.kt`: imagem de fundo em tela larga, degradês de legibilidade, ações Assistir e Detalhes, indicadores e rotação pausada ao receber foco. Busca backdrops do provedor para os destaques; se não houver, mostra o cartaz sem desfocar. As listas usam os mesmos cards compactos 2:3 do catálogo. |
| `libs/workspace/dashboard/feature/src/lib/rails/_dashboard-rail-geometry.scss` e `libs/ui/styles/_content-grid.scss` | Cards compactos com cantos de 12 dp, arte 2:3, título, indicador de progresso e avaliação; uma coluna vertical de categorias e outra com grade e paginação em `ui/browse/BrowseScreen.kt`. O mesmo tamanho é usado nos trilhos da Home. |
| `libs/ui/styles/_portal-sidebar.scss` e `unified-live-tab.component.{html,scss}` | Categorias, lista de canais, seleção escura translúcida, prévia Media3 e programação em `ui/live/LiveScreen.kt`. As três colunas permanecem lado a lado em paisagem, inclusive no celular. |
| Telas de detalhes VOD/séries | `ui/detail/DetailScreen.kt`: poster e backdrop com scrim, metadados, reprodução/favorito e temporadas/episódios do painel. |
| Configurações | `ui/settings/SettingsScreen.kt`: painéis escuros, campos nativos e ações para as credenciais Xtream, sincronização do catálogo e EPG. |
| Multitela | Não há seção equivalente no web de referência. `ui/multi/MultiScreen.kt` usa os mesmos tokens e até quatro instâncias independentes de Media3, com um só áudio por vez; é uma adaptação nativa solicitada, não uma cópia de uma tela inexistente. |

A imagem de fundo enviada pelo usuário é incorporada em `res/drawable-nodpi/app_background.png` e aparece no login e no aplicativo com uma camada escura para preservar o contraste. As dimensões são adaptadas para diferentes telas e densidades em paisagem. Cartazes/backdrops reais vêm do provedor. O modo `demo` usa uma amostra compactada da arte ficcional do mock de divulgação da referência, identificada como demonstração.

A referência é distribuída sob MIT. Os avisos de copyright/licença estão em `LICENSES/` e também no aplicativo. A marca e o logotipo IPTVnator não foram reutilizados; o aplicativo mantém seu nome e ícone próprios.
