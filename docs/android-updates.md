# Atualizações Android sem apagar dados

O Android aceita uma atualização por cima quando o `applicationId` é igual, o
`versionCode` não retrocede e o certificado de assinatura corresponde ao instalado.
O nome da versão, sozinho, não resolve a assinatura.

## Configuração permanente

No GitHub, em **Settings → Secrets and variables → Actions**, configure:

- `ANDROID_KEYSTORE_BASE64`: conteúdo base64 do keystore de assinatura original;
- `ANDROID_KEYSTORE_PASSWORD`: senha do keystore;
- `ANDROID_KEY_ALIAS`: alias da chave;
- `ANDROID_KEY_PASSWORD`: senha da chave.

Use a mesma chave em todas as versões e guarde uma cópia segura fora do GitHub.
Nunca comite o keystore ou suas senhas. O CI decodifica a chave somente no diretório
temporário do runner e não publica a chave nos artifacts.

Cada execução do workflow usa `versionCode = 1000 + GITHUB_RUN_NUMBER`.
Para builds locais, forneça `ANDROID_VERSION_CODE` com um número maior que o
instalado e as quatro variáveis `RELEASE_KEYSTORE_PATH`,
`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

## Limitação das versões anteriores

A versão 2.7.0 entregue nesta sessão foi assinada com a chave de debug temporária
do runner. Certificado SHA-256:

`cb73685308025d71382056ad5baadd0b5d9a77ac091ed5b5d30f6b5fa67d949e`

A chave privada correspondente não foi persistida no repositório nem nos
artifacts. Não é possível recuperá-la apenas do APK ou do certificado público.
Se não existir uma cópia desse keystore, uma chave nova **não** atualiza aquela
instalação por cima. Não desinstale a instalação anterior se precisar preservar os
dados locais. Obter o keystore original é o único caminho suportado aqui para
uma atualização direta daquela instalação.

Sem os secrets, o workflow emite um aviso e gera apenas um APK de teste com
assinatura temporária. Ele não deve ser apresentado como atualização compatível.
Nenhuma rotina do aplicativo desinstala versões anteriores ou apaga dados durante
uma atualização compatível.
