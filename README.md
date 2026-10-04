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

## Login, token e autorização

A API não renderiza páginas de login. O frontend autentica com Google ou Sign in with Apple, obtém o ID token assinado pelo provider e o envia como `Authorization: Bearer <id-token>` e `X-Auth-Provider: google|apple`. O header escolhe somente um tenant OIDC previamente configurado; Quarkus valida assinatura, issuer, validade e audience contra o provider. Nunca confie no header como identidade. Não há senha, sessão de servidor ou JWT próprio.

Para Google, crie OAuth Client ID para os tipos de frontend necessários no Google Cloud Console, configure consent screen e URLs autorizadas. Envie escopos `openid profile email`, use o Client ID correspondente como audience e configure `GOOGLE_CLIENT_ID`. O backend não precisa de client secret para validar bearer ID tokens. Se outro componente trocar authorization codes, guarde o secret somente nesse componente.

Para Apple web, habilite Sign in with Apple num App ID principal, crie e associe um Services ID, cadastre domínio e return URL HTTPS e use o Services ID como `APPLE_SERVICES_ID`/audience. Uma Key criada no Certificates, Identifiers & Profiles fornece Key ID, Team ID e arquivo `.p8`; este fluxo bearer não os usa na API. Eles só são necessários no componente que troca authorization codes e gera client-secret JWT. Mantenha-os em secret manager, nunca Git. O redirect web da Apple não pode usar `localhost` nem IP; use domínio HTTPS de desenvolvimento/túnel público. Apple pode fornecer o email apenas no primeiro consentimento; o `sub`, nunca o email, é a chave de identidade.

O OIDC está desligado por padrão em dev, portanto a aplicação e os testes não dependem de credenciais Google/Apple. Para ativar dev, forneça o client ID e habilite `GOOGLE_OIDC_ENABLED` ou `APPLE_OIDC_ENABLED`. Em prod os dois tenants são habilitados e os client IDs são obrigatórios. A autenticação é stateless e iniciada pelo frontend; a callback/redirect pertence ao fluxo do frontend ou a um backend intermediário, não a uma página HTML desta API.

Após validar o token, `CurrentPersonService` usa `provider + sub` para encontrar/criar `ExternalIdentity` e `Person` na mesma transaction. `SecurityIdentity` representa a identidade autenticada atual (conceitualmente próximo ao principal/contexto do Spring Security); `StoreAuthorizationService` consulta o `StoreMember` da loja solicitada. O papel é contextual e não é copiado para claims globais. OWNER administra membros; OWNER/MANAGER administram catálogo; qualquer membro consulta e registra estoque.

## CORS, variáveis e configuração

CORS permite em dev `http://localhost:3000`, somente métodos e headers usados pela API e sem cookies. Produção deve definir `CORS_ORIGINS` com as origens HTTPS exatas do frontend; não use `*`. Veja [.env.example](.env.example), que contém apenas placeholders:

- `GOOGLE_CLIENT_ID`, `APPLE_SERVICES_ID`, `GCP_PROJECT_ID`;
- `GOOGLE_APPLICATION_CREDENTIALS` (caminho externo à pasta versionada; em cloud prefira identidade gerenciada);
- `CORS_ORIGINS`, `NOTIFICATION_API_URL`;
- `OTEL_TRACES_EXPORTER` (`none` ou `otlp`) e `OTEL_EXPORTER_OTLP_ENDPOINT`.

No perfil `prod`, tracing de entrada HTTP e chamadas REST Client é exportado somente se `OTEL_TRACES_EXPORTER=otlp`; por padrão não há collector obrigatório. Trace e span propagam contexto quando o destino aceita os headers W3C. Não são adicionados Prometheus, dashboards ou collector local.

## API principal

Todos os endpoints da API exigem identidade autenticada. Como OIDC fica desligado por padrão em dev, habilite um tenant e configure seu client ID antes de chamar recursos protegidos; nos testes, `@TestSecurity` com `@OidcSecurity` fornece uma identidade e claims simulados sem usar provedores reais.

| Método e caminho | Acesso |
| --- | --- |
| `GET /auth/me` | perfil atual |
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
