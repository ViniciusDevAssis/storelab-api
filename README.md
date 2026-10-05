# StoreLab API

API JSON de estudo para cadastro e administração de lojas, catálogo e estoque. O projeto mostra como levar conceitos conhecidos de Spring Boot para Quarkus sem transformar Firestore em JPA nem introduzir camadas genéricas.

## Stack e arquitetura

- Java 21, Quarkus 3.40.1 e Maven 3.9.16.
- Quarkus REST, CDI, OIDC e Bean Validation.
- Cloud Firestore como única persistência; o cliente `Firestore` vem da extensão Quarkiverse.
- SmallRye OpenAPI, REST Client, Fault Tolerance e OpenTelemetry.
- Fluxo: HTTP Resource → Service → Repository específico → Firestore.

Modelos HTTP são records dedicados; os snapshots e tipos do SDK ficam na fronteira dos repositories. Confira o [STUDY-GUIDE](STUDY-GUIDE.md) para a trilha de leitura e os paralelos com Spring.

## Requisitos e execução local

- JDK 21. Use o Maven Wrapper (`./mvnw` no Linux/macOS ou `mvnw.cmd` no Windows); ele fixa Maven 3.9.16.
- Docker em execução para o Firestore Dev Service. Dev e testes usam um emulador local, sem credenciais de produção.

```powershell
./mvnw quarkus:dev
# Windows: .\mvnw.cmd quarkus:dev
```

Quarkus Live Coding recompila classes e atualiza a aplicação durante o desenvolvimento. Em dev, Swagger UI fica em `http://localhost:8080/q/swagger-ui`; OpenAPI JSON fica em `/q/openapi`. Para compilar e testar:

```powershell
./mvnw -B -DskipTests compile
./mvnw -B test
# Windows: .\mvnw.cmd -B -DskipTests compile
# Windows: .\mvnw.cmd -B test
```

O perfil `test` inicia o mesmo emulador por Dev Services e desliga exportação de traces. O perfil `prod` desativa credenciais de emulador, habilita OIDC Google/Apple e usa Application Default Credentials (por exemplo, workload identity ou `GOOGLE_APPLICATION_CREDENTIALS`). Nunca coloque a chave de service account no repositório.

## Firestore

Dev Services inicia o emulador Firestore em container quando Docker está disponível. A propriedade `quarkus.google.cloud.firestore.devservice.enabled` pode ser desligada. Para usar um emulador externo, defina `QUARKUS_GOOGLE_CLOUD_FIRESTORE_HOST_OVERRIDE=localhost:8080`, habilite credenciais de emulador e desligue o Dev Service. Em produção, configure `GCP_PROJECT_ID`/`QUARKUS_GOOGLE_CLOUD_PROJECT_ID` e ADC; a extensão usa credenciais externas.

| Dados | Caminho |
| --- | --- |
| Pessoa | `people/{personId}` |
| Identidade externa | `externalIdentities/{sha256(provider:subject)}` |
| Loja | `stores/{storeId}` |
| Membro | `stores/{storeId}/members/{personId}` |
| Categoria | `categories/{categoryId}` |
| Produto | `products/{productId}` |
| Histórico imutável | `products/{productId}/inventoryMovements/{movementId}` |
| Migration | `schemaMigrations/{version}` |

As IDs curtas nos documentos mantêm relações documentais explícitas. A subcollection de membros mantém membros junto da loja; `storeId` e `personId` são repetidos nela para consultas collection-group. O movimento fica sob o produto e também guarda `storeId` para filtrar/auditar. `Instant` é convertido para `java.util.Date` no repository, formato aceito pelo SDK.

Saídas e entradas de estoque usam uma única Firestore Transaction: lê produto, valida estado e quantidade, atualiza estoque e cria o movimento. Isso evita que duas saídas concorrentes vendam o mesmo saldo e mantém o evento histórico consistente com o saldo. Firestore transaction não é uma transação JPA `@Transactional`: só cobre leituras/escritas no banco Firestore e o SDK pode repetir o callback em conflitos; efeitos externos ficam depois do commit.

Produtos aceitam filtros `categoryId` e `active`, `limit` entre 1 e 100 e `cursor` opaco na URL. A resposta contém `items`, `nextCursor` e `hasMore`. Os índices compostos usados por combinações de filtros e histórico estão em `firestore.indexes.json`. A consulta de membros por pessoa atravessa subcollections homônimas com `collectionGroup("members")`; seu índice de campo simples usa escopo `COLLECTION_GROUP`, enquanto os índices locais de produtos/histórico usam escopo `COLLECTION`.

## Login social e sessão

O frontend tem apenas UI, navegação, formulários e chamadas HTTP. Ele não autentica com os providers, não recebe tokens e não envia `Authorization: Bearer` nem `X-Auth-Provider`.

```text
Modelo antigo: Browser → Google/Apple → ID Token → Bearer → Quarkus
Modelo atual:  Browser → Quarkus → Google/Apple → Quarkus → sessão HttpOnly → Browser
```

Os tenants OIDC `google` e `apple` usam `application-type=web-app`. OIDC do Quarkus inicia o Authorization Code Flow, gera e valida `state`, troca o authorization code, valida os tokens e cria a sessão. O cookie de sessão é HttpOnly, path `/` e gerenciado pelo Quarkus; JavaScript não pode lê-lo. Dev usa HTTP localhost e não força `Secure`; prod força `Secure` e configura `SameSite` para `none`, necessário quando frontend e API são sites distintos. A API não mantém tokens próprios, refresh tokens próprios ou um SessionRepository.

O navegador inicia o login ao navegar para `GET http://localhost:8080/auth/google/login` ou `GET http://localhost:8080/auth/apple/login`. O Quarkus retorna ao endpoint de entrada depois do callback, cria ou localiza a pessoa por `provider + sub` e redireciona para `${FRONTEND_URL}/dashboard`. O destino é configuração fixa do backend, nunca um parâmetro livre da requisição. Callbacks são processados pelo OIDC do Quarkus, sem troca manual de código:

- Google: `http://localhost:8080/auth/google/callback`.
- Apple: `/auth/apple/callback` (em HTTPS com domínio validado pela Apple).

Uma requisição REST anônima, incluindo `GET /auth/me` e endpoints de negócio, recebe `401` JSON; ela nunca inicia login nem retorna uma página HTML. `GET /auth/me` retorna `MeResponse` quando existe sessão. `POST /auth/logout` encerra somente a sessão local StoreLab com `OidcSession.logout()`, limpa o cookie OIDC local e retorna `204`; não encerra a conta Google/Apple do navegador. O `AuthenticationRequestFilter` não autentica: apenas padroniza o `401` e deixa os endpoints de entrada/callback/logout passarem ao mecanismo correspondente.

`SecurityIdentity` é a identidade autenticada da requisição. `CurrentPersonService` lê explicitamente o ID Token injetado com `@IdToken`, usa `idToken.getSubject()` como `sub` e obtém o provider do tenant OIDC autenticado. A identidade persistida continua sendo `ExternalIdentity(provider, sub)`, nunca o email. O primeiro login cria `Person` e `ExternalIdentity`; os seguintes recuperam a mesma pessoa. Um tenant/provider ausente ou desconhecido falha fechado. As regras contextuais de `StoreAuthorizationService` permanecem baseadas no membro e papel da Store requisitada.

### Google

No Google Cloud Console, crie um OAuth Client para aplicação Web e cadastre exatamente `http://localhost:8080/auth/google/callback` como Authorized Redirect URI. Configure a tela de consentimento e os scopes `openid`, `email` e `profile`. No backend, defina `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` e `FRONTEND_URL=http://localhost:3000`; nunca coloque o Client Secret no Next.js. Em dev, habilite o tenant com `GOOGLE_OIDC_ENABLED=true`. O provider `google` do Quarkus fornece os endpoints OIDC e o Authorization Code Flow.

Validação manual ponta a ponta:

1. Configure as três variáveis acima apenas no ambiente do backend e inicie o Firestore Emulator/Docker.
2. Inicie a API com `JAVA_HOME` apontando para JDK 21 e `./mvnw quarkus:dev` (Windows: `./mvnw.cmd quarkus:dev`).
3. Inicie o Next.js em `http://localhost:3000` e navegue o browser para `http://localhost:8080/auth/google/login`.
4. Complete o login Google. O callback deve ser `http://localhost:8080/auth/google/callback`, e o browser deve voltar a `http://localhost:3000/dashboard` com o cookie HttpOnly da API.
5. Do frontend, chame `GET http://localhost:8080/auth/me` e `GET http://localhost:8080/stores` com `credentials: "include"`; ambos devem reconhecer a sessão.
6. Chame `POST http://localhost:8080/auth/logout` com `credentials: "include"` e JSON; a resposta é `204` e a próxima chamada a `/auth/me` retorna `401` JSON.

### Apple

Apple é opcional e permanece desabilitada até haver configuração real (`APPLE_OIDC_ENABLED=true`). A integração usa o provider Apple suportado pelo Quarkus e seu client-secret JWT assinado; a API precisa receber `APPLE_SERVICES_ID`, `APPLE_KEY_ID`, `APPLE_TEAM_ID` e o caminho externo `APPLE_PRIVATE_KEY_PATH` para a chave `.p8`. Não gere nem versione client secret, `.p8` ou outra chave privada.

No Apple Developer, habilite Sign in with Apple no App ID principal, crie um Services ID ligado a esse App ID, cadastre o domínio e a Return URL, e gere uma chave Sign in with Apple. O Key ID, Team ID e `.p8` pertencem apenas ao backend. Apple web exige domínio e Return URL verificados em HTTPS; `localhost` e IP não são aceitos para a validação real. O callback configurado no backend é `/auth/apple/callback`; use o endereço HTTPS público correspondente na configuração Apple. A integração usa `form_post` para os scopes `name` e `email`; o email pode ser fornecido somente no primeiro consentimento. A validação Apple real depende da conta/configuração Apple Developer e de domínio HTTPS.

### CORS, cookies e variáveis

CORS permite `http://localhost:3000` em dev, com `allow-credentials=true`, métodos `GET, POST, PATCH, DELETE, OPTIONS` e headers `Accept, Content-Type`. Em prod, `CORS_ORIGINS` deve conter somente as origens HTTPS exatas do frontend; nunca use `*` com credentials. O frontend envia `credentials: "include"`. As alterações aceitam JSON, que exige preflight cross-origin; CORS restringe quais origens podem fazer chamadas autenticadas pelo browser. Cookies usam `SameSite` apropriado ao ambiente.

Veja [.env.example](.env.example), que contém somente placeholders e caminhos ilustrativos:

- `FRONTEND_URL`, `CORS_ORIGINS`;
- `OIDC_COOKIE_SAME_SITE` (opcional; em prod o padrão é `none`, junto com cookie `Secure`);
- `GOOGLE_OIDC_ENABLED`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`;
- `APPLE_OIDC_ENABLED`, `APPLE_SERVICES_ID`, `APPLE_KEY_ID`, `APPLE_TEAM_ID`, `APPLE_PRIVATE_KEY_PATH`;
- `GCP_PROJECT_ID`, `GOOGLE_APPLICATION_CREDENTIALS` (caminho externo ao repositório; em cloud prefira identidade gerenciada);
- `NOTIFICATION_API_URL`, `OTEL_TRACES_EXPORTER` (`none` ou `otlp`) e `OTEL_EXPORTER_OTLP_ENDPOINT`;
- `FIRESTORE_ENABLE_TRACING=OFF`, mantido por causa do conflito conhecido entre tracing gRPC interno do SDK Firestore e a instrumentação OpenTelemetry.

No perfil `dev`, OIDC Google e Apple ficam desligados por padrão; habilite somente o provider configurado. Em `prod`, Google fica habilitado e requer Client ID/Secret. Apple continua opcional. Os testes simulam identidades localmente e não acessam Google nem Apple.

No perfil `prod`, tracing de entrada HTTP e chamadas REST Client é exportado somente se `OTEL_TRACES_EXPORTER=otlp`; por padrão não há collector obrigatório. Trace e span propagam contexto quando o destino aceita os headers W3C. Não são adicionados Prometheus, dashboards ou collector local.

## API principal

Endpoints de negócio e `/auth/me` exigem identidade da sessão; chamadas anônimas recebem `401` JSON. Login começa somente nos dois caminhos explícitos abaixo. Nos testes, `@TestSecurity` com `@OidcSecurity` fornece identidade e ID Token simulados sem chamar provedores reais.

| Método e caminho | Acesso |
| --- | --- |
| `GET /auth/google/login` | inicia Google ou retorna ao dashboard se já houver sessão |
| `GET /auth/apple/login` | inicia Apple ou retorna ao dashboard se já houver sessão |
| `GET /auth/me` | perfil da sessão atual |
| `POST /auth/logout` | encerra a sessão StoreLab local e retorna `204` |
| `POST /stores`, `GET /stores` | criar/listar lojas da pessoa |
| `GET /stores/{storeId}`, `PATCH /stores/{storeId}` | membro / OWNER |
| `GET,POST /stores/{storeId}/members` | OWNER |
| `PATCH,DELETE /stores/{storeId}/members/{personId}` | OWNER |
| `GET,POST /stores/{storeId}/categories` | membro consulta; OWNER/MANAGER altera |
| `PATCH /stores/{storeId}/categories/{categoryId}` | OWNER/MANAGER |
| `GET,POST /stores/{storeId}/products` | membro consulta; OWNER/MANAGER cria |
| `GET,PATCH /stores/{storeId}/products/{productId}` | membro consulta; OWNER/MANAGER altera |
| `GET,POST /stores/{storeId}/products/{productId}/inventory-movements` | membro |

Criações retornam `201`, remoções `204`, leituras/alterações `200`. Erros usam JSON com `status`, `code`, `message`, `path`, `timestamp` e lista `validationErrors`; respostas não incluem snapshots nem stack traces.

Uma única migration versionada adiciona `active=true` apenas a documentos de produto sem o campo. O marcador só é gravado após os documentos, então uma reexecução após interrupção é segura. Em document database ela migra dados existentes; não cria nem altera schema rígido SQL.

## Integração de notificação

Quando uma movimentação deixa o saldo em zero, `NotificationClient` faz POST configurável para `/notifications/stock-empty`. É uma integração didática, sem conta/serviço pago; indisponibilidade não desfaz o estoque já confirmado. Timeout, retry limitado e circuit breaker ficam no único serviço externo. Retry reutiliza o ID do movimento como chave de idempotência; o receptor real precisa respeitá-la.
