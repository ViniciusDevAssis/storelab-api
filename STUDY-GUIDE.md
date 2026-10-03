# StoreLab API — trilha de estudo

Este repositório é um laboratório executável de Quarkus e Firestore para quem já conhece Spring Boot. Leia por fluxo e acompanhe cada chamada do HTTP até a persistência. Os comentários no código se concentram nos pontos em que o comportamento difere do Spring.

## Comece pelo fluxo

Uma chamada de catálogo percorre:

```text
HTTP/JSON → ProductResource → ProductService → ProductRepository → Firestore
```

Autenticação e autorização seguem caminhos diferentes:

```text
Frontend → Google ou Apple → bearer ID token → OIDC → SecurityIdentity
         → ExternalIdentity → Person → StoreMember → papel na Store pedida
```

O token prova quem fez a chamada. Ele não contém o papel de cada loja. O papel vem do documento de membro da loja específica, por isso uma pessoa pode ser OWNER numa loja e EMPLOYEE em outra.

## Sequência recomendada

1. **Visão geral e estrutura** — comece por este guia e pelo [README.md](README.md); compare as pastas `api`, `auth`, `category`, `common`, `domain`, `integration`, `inventory`, `migration`, `person`, `product`, `security` e `store`.
2. **`pom.xml` e extensões** — leia as dependências do [pom.xml](pom.xml). Cada extensão está ali por um recurso observável: REST/JSON, CDI, Firestore, OIDC, validação, OpenAPI, REST Client, Fault Tolerance ou tracing.
3. **Bootstrap e profiles** — leia [application.properties](src/main/resources/application.properties). Quarkus descobre beans e configura extensões durante o build; `%dev`, `%test` e `%prod` ajustam o runtime sem criar um sistema de configuração próprio.
4. **CDI e escopo** — abra [StoreService.java](src/main/java/com/viniciusdevassis/storelab/store/StoreService.java) e [StoreRepository.java](src/main/java/com/viniciusdevassis/storelab/store/StoreRepository.java). `@ApplicationScoped` registra um bean CDI compartilhado; em Spring, a analogia comum é um bean singleton `@Service`/`@Repository`, mas os modelos de escopo e inicialização não são os mesmos.
5. **Resource REST** — leia [StoreResource.java](src/main/java/com/viniciusdevassis/storelab/store/StoreResource.java). `@Path`, `@GET`, `@POST`, `@PATCH` e `@DELETE` vêm de Jakarta REST; compare com `@RestController`, `@GetMapping` e demais mappings Spring.
6. **Service** — acompanhe `StoreResource` até `StoreService`. A regra de aplicação fica fora do Resource, e o Resource traduz HTTP em DTOs e status.
7. **Repository específico** — siga até `StoreRepository`, [ProductRepository.java](src/main/java/com/viniciusdevassis/storelab/product/ProductRepository.java) ou [InventoryRepository.java](src/main/java/com/viniciusdevassis/storelab/inventory/InventoryRepository.java). São classes concretas orientadas a consultas/casos de uso; não há `JpaRepository` nem interface genérica.
8. **DTOs e domínio** — compare [ApiDtos.java](src/main/java/com/viniciusdevassis/storelab/api/ApiDtos.java) com [Models.java](src/main/java/com/viniciusdevassis/storelab/domain/Models.java). Records tornam os contratos imutáveis. Os recursos mapeiam explicitamente sem framework mapper.
9. **Bean Validation** — veja anotações em `ApiDtos` e `@Valid` nos Resources. `@NotBlank`, `@NotNull`, `@Positive`, `@PositiveOrZero` e `@Size` validam forma; regras como saldo suficiente pertencem ao Service/Repository.
10. **Erros HTTP** — abra [ApiException.java](src/main/java/com/viniciusdevassis/storelab/common/ApiException.java), [ApiError.java](src/main/java/com/viniciusdevassis/storelab/common/ApiError.java) e [ApiExceptionMapper.java](src/main/java/com/viniciusdevassis/storelab/common/ApiExceptionMapper.java). `ExceptionMapper` é um provider Jakarta REST; a ideia lembra `@ControllerAdvice`, mas a seleção ocorre pelo tipo de exceção e pelo mecanismo JAX-RS.
11. **Configuração externa** — confira [.env.example](.env.example). É um arquivo de nomes e placeholders, não um lugar para credenciais verdadeiras. Quarkus também lê variáveis com nomes de propriedades em maiúsculas e sublinhados.
12. **Profiles** — volte a `application.properties`: configuração sem prefixo é comum, `%dev` é desenvolvimento, `%test` isola testes e `%prod` aplica requisitos de produção.
13. **Firestore client** — procure a injeção `Firestore` em `ProductRepository`. A extensão Quarkiverse produz o cliente CDI; o domínio não importa o SDK.
14. **Collections e documents** — compare `people/{id}`, `products/{id}` e `stores/{id}` usados nos repositories com a tabela do README. O documento pode carregar campos de filtro e IDs de relacionamento.
15. **Subcollections** — examine `stores/{storeId}/members/{personId}` em [StoreMemberRepository.java](src/main/java/com/viniciusdevassis/storelab/store/StoreMemberRepository.java) e `products/{productId}/inventoryMovements/{movementId}` em `InventoryRepository`. A hierarquia facilita ler o agregado e manter o histórico com o produto.
16. **IDs em vez de referências** — os documentos guardam strings de ID, não `DocumentReference`. Isso deixa os modelos simples e impede que classes de domínio dependam do SDK.
17. **Desnormalização intencional** — leia os mapas gravados por `StoreMemberRepository` e `InventoryRepository`: `storeId` aparece junto ao documento subordinado para buscas/controle de acesso sem carregar uma árvore completa.
18. **Queries** — veja `ProductRepository.page`. Cada igualdade corresponde a um caso de uso real; não existe `contains` fingindo ser pesquisa full-text.
19. **Índices** — compare os filtros de produto e do histórico com [firestore.indexes.json](firestore.indexes.json). A consulta `collectionGroup("members").whereEqualTo("personId", ...)` precisa de um índice de campo simples cujo `queryScope` é `COLLECTION_GROUP`; isso alcança subcollections `members` em diferentes lojas, ao contrário dos índices com escopo `COLLECTION`. Índices compostos acompanham consultas compostas reais, e seu deploy pode levar tempo no Firestore gerenciado.
20. **Cursor e paginação** — siga `page` em `ProductRepository` até `Page` em `ApiDtos`. O endpoint pede um item extra para saber se há próxima página e codifica o ID em cursor URL-safe; nunca retorna `DocumentSnapshot`.
21. **Transactions** — este é o fluxo central: abra [InventoryService.java](src/main/java/com/viniciusdevassis/storelab/inventory/InventoryService.java), depois [InventoryRepository.java](src/main/java/com/viniciusdevassis/storelab/inventory/InventoryRepository.java) e [InventoryResource.java](src/main/java/com/viniciusdevassis/storelab/inventory/InventoryResource.java). A transaction lê saldo/estado, valida disponibilidade, atualiza o produto e cria o evento imutável.
22. **Firestore transaction versus `@Transactional` JPA** — a transação do SDK coordena documentos Firestore e pode repetir seu callback quando há contenção. Não cobre HTTP externo, não participa de uma unidade de trabalho JPA e exige que as leituras venham antes das escritas da transação.
23. **Firestore Emulator** — leia `quarkus.google.cloud.firestore.devservice.enabled` em `application.properties`. O Dev Service usa container do emulador e dispensa projeto real/credenciais; Docker precisa estar ativo. Para emulador externo, defina host override e desligue o Dev Service.
24. **Dev Services** — extension-managed, inicia o container em dev/test e conecta o bean `Firestore` ao endpoint local. Desative com a configuração da extensão para conectar manualmente a um emulator externo.
25. **Quarkus Security** — abra [GoogleAppleTenantResolution.java](src/main/java/com/viniciusdevassis/storelab/security/GoogleAppleTenantResolution.java) e [AuthenticationRequestFilter.java](src/main/java/com/viniciusdevassis/storelab/security/AuthenticationRequestFilter.java). OIDC valida o token e cria a identidade; o filter não autentica: apenas padroniza o JSON 401 para identidade anônima. `SecurityIdentity` continua sendo fornecida pelo mecanismo de segurança do Quarkus. O header só seleciona Google ou Apple entre configurações fixas.
26. **`SecurityIdentity`** — acompanhe [CurrentPersonService.java](src/main/java/com/viniciusdevassis/storelab/auth/CurrentPersonService.java). `SecurityIdentity` representa a identidade autenticada desta requisição. É conceitualmente próximo a principal e contexto do Spring Security; use-o na fronteira de segurança, não espalhe-o pelos modelos/repositories.
27. **OIDC bearer** — o projeto aceita bearer token do cliente. OIDC valida assinatura/chaves públicas, issuer, expiração e audience antes de entregar a identidade ao código. Em produção use HTTPS e guarde tokens fora de logs.
28. **Google** — leia a configuração `quarkus.oidc.google.*` e a seção Login do README. O Client ID esperado como audience deve corresponder ao token que o frontend envia; peça `openid profile email` se o app precisar dos claims de nome/email.
29. **Sign in with Apple** — leia `quarkus.oidc.apple.*` e o README. Para web, Apple exige Services ID ligado a um App ID e domínio/return URL registrados; URL de redirect web não aceita localhost/IP.
30. **Dois providers** — `X-Auth-Provider` encaminha ao tenant estático correspondente. Isso evita autenticar um token Apple contra chaves Google e mantém a seleção visível; qualquer header não confiável continua sem substituir validação de assinatura/audience.
31. **`ExternalIdentity` e `Person`** — veja `PersonRepository.findOrCreate`: a chave é SHA-256 de `provider:subject`, não email. A primeira associação grava pessoa e identidade em uma transação; a mesma identidade posterior encontra a pessoa.
32. **Autorização contextual** — abra [StoreAuthorizationService.java](src/main/java/com/viniciusdevassis/storelab/security/StoreAuthorizationService.java), depois `StoreMemberRepository` e `ProductService`. Cada operação consulta o papel naquela `storeId`; papel de outra Store nunca é reutilizado.
33. **REST Client** — leia [NotificationClient.java](src/main/java/com/viniciusdevassis/storelab/integration/NotificationClient.java) e [NotificationService.java](src/main/java/com/viniciusdevassis/storelab/integration/NotificationService.java). A integração só ocorre quando o saldo chega a zero e usa URL configurável.
34. **Fault Tolerance** — no `NotificationService`, `@Timeout` limita espera, `@Retry` tenta novamente com o mesmo ID idempotente e `@CircuitBreaker` interrompe chamadas quando o destino falha repetidamente. No Spring, compare com Resilience4j; a API de annotations e integração são diferentes. Retry em operações não idempotentes pode duplicar efeitos.
35. **OpenTelemetry** — confira as propriedades `quarkus.otel.*`. Quarkus instrumenta HTTP/REST Client e propaga contexto W3C; collector é opcional e exportação pode ficar desligada localmente.
36. **OpenAPI** — veja tags e summaries em `StoreResource`, `ProductResource` e `InventoryResource`; `/q/openapi` expõe o contrato e Swagger UI aparece em dev.
37. **Migrations** — leia [MigrationRunner.java](src/main/java/com/viniciusdevassis/storelab/migration/MigrationRunner.java) e [V001AddActiveToExistingProducts.java](src/main/java/com/viniciusdevassis/storelab/migration/V001AddActiveToExistingProducts.java). O marcador é gravado no final; a operação só preenche campo ausente, então reexecutar é seguro. É migração de dados, não DDL.
38. **Testes** — abra [StoreLabApiTest.java](src/test/java/com/viniciusdevassis/storelab/StoreLabApiTest.java). `@QuarkusTest` sobe a aplicação; `@TestSecurity` cria a identidade e `@OidcSecurity` fornece claims OIDC simulados, inclusive `sub`; RestAssured verifica o contrato HTTP sem Google/Apple reais. Cada cenário prepara seus próprios dados.
39. **Spring Boot × Quarkus** — Resource `@Path` corresponde ao papel do `@RestController`; `@GET` a `@GetMapping`; CDI `@ApplicationScoped`/`@Inject` a beans Spring; `ExceptionMapper` a parte do trabalho de `@ControllerAdvice`; `SecurityIdentity` a principal/contexto; Firestore repository direto a consultas de um client, não Spring Data; transaction SDK a transação de documento, não JPA; `@QuarkusTest` + RestAssured a testes integrados como MockMvc. São analogias para aprender, não equivalências 1:1.

## Perfis e limites desta versão didática

- **dev:** Swagger ligado, CORS localhost e Firestore emulator; OIDC externo desligado por padrão.
- **test:** Firestore emulator e identidades substitutas; nenhum token real de Google/Apple.
- **prod:** OIDC exige ambos client IDs, Firestore requer ADC/projeto configurado e CORS deve listar origens reais.

O fluxo Google/Apple é bearer OIDC para frontend separado. Esta versão não fornece uma UI, callback de login, troca de authorization code, refresh token próprio nem client secret social. O `.p8` Apple é apenas para o componente que eventualmente fizer a troca de código. As notificações são uma amostra sem serviço externo embutido. Native Image não é o objetivo deste laboratório.
