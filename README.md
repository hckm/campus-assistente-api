# campus-assistente-api

API backend em Java 21 e Spring Boot 3 para simular um chatbot de atendimento administrativo e comercial para farmacia de manipulacao.

O chatbot nao prescreve medicamentos, nao indica formulas, nao sugere dosagens e nao substitui farmaceutico, medico ou outro profissional de saude. Mensagens sensiveis sao encaminhadas para atendimento humano.

## Planejamento da migração para Azure

A migração para Azure Cosmos DB, Azure OpenAI no Microsoft Foundry e Azure App Service está documentada no [plano de migração](docs/plano-migracao-azure-cosmos-db.md) e no [ADR-0001](docs/adr/0001-azure-cosmos-db.md). A persistência Cosmos está disponível no perfil `cosmos` e o adaptador Azure OpenAI é habilitado explicitamente por `IA_PROVIDER=AZURE_OPENAI`. O deployment continua configurável; nenhum modelo é fixado pelo código.

## Tecnologias

- Java 21
- Spring Boot 3.4.5
- Spring Web
- Spring Data JPA
- Bean Validation
- Lombok
- PostgreSQL
- H2 para testes e perfil local
- Maven
- Swagger / OpenAPI (springdoc 2.8.6)
- Azure OpenAI Chat Completions API v1 via HTTP client nativo do Java
- Azure Identity para Cosmos DB e Azure OpenAI

## Arquitetura geral

```
Cliente (WhatsApp / Portal)
        │
        ▼
  PortalChatController / WhatsAppWebhookController
        │
        ▼
  ChatbotService
  ├── EnderecoEnrichmentService   (enriquece CEP/endereco da mensagem)
  ├── InterpretacaoIaService      (seleciona Gemini, Azure OpenAI ou Codex CLI local)
  ├── PromptBuilderService        (monta prompt multi-turn e contexto limitado)
  └── AtendimentoService          (persiste e gerencia estado)
        │
        ▼
  Azure Cosmos DB / PostgreSQL / H2
```

### Sessao e historico de conversa

O chatbot mantem sessoes por telefone do cliente. Enquanto a janela de inatividade nao expirar, novas mensagens do mesmo telefone continuam o atendimento ativo. O historico de mensagens (cliente e bot alternados) e enviado ao provedor selecionado em formato multi-turn, preservando o contexto da conversa.

### Catalogo de produtos (Renovo)

No backend JPA, a aplicacao pode carregar o arquivo `src/main/resources/data/catalogo-renovo.csv` para a tabela `catalogo_renovo`. No Cosmos, a aplicacao cria os containers e carrega `catalogo-renovo.csv` e `estabelecimento-renovo.json` somente quando os respectivos conjuntos estao vazios. Reinicios e inicializacoes concorrentes nao atualizam documentos existentes. A primeira inferencia nunca recebe linhas de produto. Quando a mensagem exige catalogo, o provedor pode solicitar uma busca validada por categoria, nome ou faixa de preco. O backend aplica o limite na consulta ao banco e faz no maximo uma segunda inferencia com os registros encontrados. Perguntas genericas recebem somente uma lista limitada de categorias para refinamento.

| Variavel | Padrao | Descricao |
|---|---|---|
| `CATALOGO_CONSULTA_IA_LIMITE_ITENS` | `8` | Itens ou categorias retornados para a IA, com limite defensivo de 20. |
| `CATALOGO_CONSULTA_IA_MAX_CARACTERES_CONTEXTO` | `6000` | Limite de caracteres do contexto de catalogo enviado na segunda inferencia. |
| `CATALOGO_CONSULTA_IA_MAX_BYTES_CONTEXTO` | `12000` | Limite UTF-8 do contexto de catalogo enviado na segunda inferencia. |

Formato do CSV (7 colunas, com cabecalho):

```
codigoCatalogo,produto,descricao,categoria,precoAtual,precoOriginal,urlCatalogo
```

### Regras de entrega por modalidade

| Modalidade | Cobertura |
|---|---|
| Correio | Todo o Brasil |
| Transportadora | Todo o Brasil |
| Motoboy | Somente cidades em `ESTABELECIMENTO_CIDADES_ATENDIDAS` |
| Retirada na loja | Presencial |

Quando o cliente nao especifica modalidade, o chatbot informa todas as opcoes. Quando especifica Motoboy para uma cidade nao atendida, o chatbot oferece Correio ou Transportadora como alternativa.

## Variaveis de ambiente

`IA_PROVIDER` seleciona `GEMINI`, `AZURE_OPENAI` ou `CODEX_CLI`. O padrão permanece `GEMINI`; sem a chave Gemini, esse adaptador usa a classificação local. Segredos devem existir somente em variáveis de ambiente ou referências protegidas do ambiente de hospedagem.

### Gemini

| Variavel | Padrao | Descricao |
|---|---|---|
| `GEMINI_API_KEY` | _(vazio)_ | Chave da API do Google AI Studio. Sem ela, o backend usa classificacao local. |
| `GEMINI_MODEL` | `gemini-2.5-flash` | Modelo do Gemini a ser utilizado. |
| `GEMINI_BASE_URL` | `https://generativelanguage.googleapis.com` | URL base da API do Gemini. |
| `GEMINI_TIMEOUT_SECONDS` | `30` | Timeout em segundos para chamadas ao Gemini. |

### Azure OpenAI no Microsoft Foundry

O adaptador usa Chat Completions API v1 em `https://<recurso>.openai.azure.com/openai/v1/chat/completions`. `AZURE_OPENAI_ENDPOINT` aceita o endpoint raiz do recurso ou o mesmo endpoint terminado em `/openai/v1`. Um endpoint de projeto Foundry no formato `https://<recurso>.services.ai.azure.com/api/projects/<projeto>` não é aceito por este adaptador.

O nome configurado em `AZURE_OPENAI_DEPLOYMENT` é enviado no campo `model`. O código não escolhe um modelo. A resposta usa JSON Schema com `strict=true` e também é validada localmente. Recusa, filtro de conteúdo, truncamento, JSON inválido, timeout, 401, 403, 429 e 5xx acionam a classificação local segura. Mensagens sem consulta de produtos fazem uma inferencia. Uma busca de catalogo valida pode fazer uma segunda inferencia com resultados limitados. O adaptador não repete requisições automaticamente.

| Variavel | Padrao | Descricao |
|---|---|---|
| `IA_PROVIDER` | `GEMINI` | Use `AZURE_OPENAI` para habilitar este adaptador. |
| `AZURE_OPENAI_ENDPOINT` | _(vazio)_ | Endpoint de inferência do recurso Azure OpenAI. Obrigatório quando o adaptador é selecionado. |
| `AZURE_OPENAI_DEPLOYMENT` | _(vazio)_ | Nome do deployment enviado como `model`. Obrigatório quando o adaptador é selecionado. |
| `AZURE_OPENAI_AUTHENTICATION` | `MANAGED_IDENTITY` | `MANAGED_IDENTITY` ou `API_KEY`. |
| `AZURE_OPENAI_MANAGED_IDENTITY_CLIENT_ID` | _(vazio)_ | Client ID UUID de uma identidade gerenciada atribuída pelo usuário. Vazio usa a identidade gerenciada atribuída pelo sistema. |
| `AZURE_OPENAI_API_KEY` | _(vazio)_ | Chave usada somente com `API_KEY`. |
| `AZURE_OPENAI_TIMEOUT` | `30s` | Limite total da chamada HTTP e da obtenção do token. |
| `AZURE_OPENAI_MAX_OUTPUT_TOKENS` | `800` | Limite enviado como `max_completion_tokens`. |
| `AZURE_OPENAI_MAX_RESPONSE_BYTES` | `65536` | Tamanho máximo aceito para o corpo HTTP. |

No Azure App Service, use `MANAGED_IDENTITY` e atribua à identidade o papel `Cognitive Services OpenAI User` no recurso Azure OpenAI. O código usa `ManagedIdentityCredential`, sem fallback para login de desenvolvedor. Para identidade atribuída pelo usuário, informe também `AZURE_OPENAI_MANAGED_IDENTITY_CLIENT_ID`. A opção `API_KEY` permite desenvolvimento local e ambientes que ainda exigem chave; a aplicação não lê arquivos de credenciais nem registra a chave.

Documentação oficial:

- [Structured Outputs no Azure OpenAI](https://learn.microsoft.com/en-us/azure/foundry/openai/how-to/structured-outputs)
- [Chat Completions API v1](https://learn.microsoft.com/en-us/azure/foundry/openai/latest)
- [Identidade gerenciada em aplicações Java hospedadas no Azure](https://learn.microsoft.com/en-us/azure/developer/java/sdk/authentication/azure-hosted-apps)

### Estabelecimento

As variaveis `ESTABELECIMENTO_*` informam ao chatbot qual cliente ele representa. O provedor selecionado recebe esses dados no prompt e o backend tambem usa regras fixas para responder horario, endereco, pagamento e entrega sem inventar informacoes.

| Variavel | Padrao | Descricao |
|---|---|---|
| `ESTABELECIMENTO_NOME` | _(vazio)_ | Nome da farmacia exibido nas respostas. |
| `ESTABELECIMENTO_TIPO` | `farmacia de manipulacao` | Tipo do estabelecimento informado ao provedor de IA. |
| `ESTABELECIMENTO_HORARIO_FUNCIONAMENTO` | _(vazio)_ | Horario de funcionamento. Ex: `Segunda a sexta das 08:00 as 18:00`. |
| `ESTABELECIMENTO_ENDERECO` | _(vazio)_ | Endereco fisico do estabelecimento. |
| `ESTABELECIMENTO_FORMAS_PAGAMENTO` | _(vazio)_ | Formas de pagamento aceitas. Ex: `Pix, cartao de credito e debito`. Vazio encaminha perguntas comuns sobre pagamento para confirmacao humana; configurado permite responde-las sem encaminhamento. |
| `ESTABELECIMENTO_CONDICOES_PARCELAMENTO` | _(vazio)_ | Texto exato sobre quantidade de parcelas e condicoes. Vazio encaminha perguntas sobre parcelamento para confirmacao humana. |
| `ESTABELECIMENTO_ENTREGA` | `Entregamos apenas nas cidades atendidas configuradas.` | Texto livre sobre a politica de entrega. Ignorado se `ESTABELECIMENTO_CIDADES_ATENDIDAS` estiver configurado. |
| `ESTABELECIMENTO_CIDADES_ATENDIDAS` | _(vazio)_ | Cidades atendidas por Motoboy, separadas por virgula. Ex: `Iacanga,Arealva,Reginopolis`. |
| `ESTABELECIMENTO_UF` | _(vazio)_ | UF padrao para busca de CEP por logradouro. Ex: `SP`. |
| `ESTABELECIMENTO_TELEFONE` | _(vazio)_ | Telefone de contato exibido nas respostas. |
| `ESTABELECIMENTO_SITE` | _(vazio)_ | Site do estabelecimento. Ex: `https://www.minhafarmacia.com.br`. |

Para entrega por Motoboy, configure `ESTABELECIMENTO_CIDADES_ATENDIDAS` com uma lista separada por virgula:

```bash
ESTABELECIMENTO_CIDADES_ATENDIDAS=Iacanga,Arealva,Reginopolis
ESTABELECIMENTO_UF=SP
```

Quando o cliente informa um CEP, o backend consulta o servico configurado em `CEP_LOOKUP_BASE_URL` para descobrir a cidade e compara com `ESTABELECIMENTO_CIDADES_ATENDIDAS`. Quando o cliente informa endereco sem CEP, o backend usa `ESTABELECIMENTO_UF`, a cidade identificada na mensagem e o logradouro para buscar o CEP no ViaCEP.

### Banco de dados

| Variavel | Padrao | Descricao |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/campus_ia_chatbot` | URL JDBC do banco. Obrigatoria em producao. |
| `DB_USERNAME` | `postgres` | Usuario do banco. Obrigatorio em producao. |
| `DB_PASSWORD` | `postgres` | Senha do banco. Obrigatoria em producao. |
| `JPA_DDL_AUTO` | `update` (local) / `validate` (prod) | Estrategia do Hibernate para o schema. |

### CEP Lookup

| Variavel | Padrao | Descricao |
|---|---|---|
| `CEP_LOOKUP_ENABLED` | `true` | Habilita ou desabilita a consulta de CEP. |
| `CEP_LOOKUP_BASE_URL` | `https://viacep.com.br` | URL base do servico de CEP. |
| `CEP_LOOKUP_TIMEOUT_SECONDS` | `5` | Timeout em segundos para consultas de CEP. |

### Sessao do chatbot

| Variavel | Padrao | Descricao |
|---|---|---|
| `CHATBOT_SESSAO_JANELA_INATIVIDADE_MINUTOS` | `30` | Tempo em minutos sem mensagem para encerrar a sessao ativa do cliente. |
| `CHATBOT_SESSAO_MAX_HISTORICO_MENSAGENS` | `10` | Numero maximo de mensagens do historico enviadas ao provedor por turno. |

### Servidor

| Variavel | Padrao | Descricao |
|---|---|---|
| `SERVER_PORT` | `8080` | Porta em que a API sera exposta. |

### Mensagens do chatbot

As respostas automaticas podem ser customizadas sem alterar codigo. Os placeholders `{nome}`, `{horario}`, `{formasPagamento}`, `{endereco}`, `{cidade}` e `{cidades}` sao substituidos dinamicamente.

| Variavel | Padrao |
|---|---|
| `CHATBOT_MSG_HORARIO_INFORMADO` | `O horario de funcionamento de {nome} e: {horario}.` |
| `CHATBOT_MSG_HORARIO_NAO_CONFIGURADO` | `Ainda nao tenho o horario de funcionamento configurado. Vou encaminhar para a equipe confirmar essa informacao.` |
| `CHATBOT_MSG_PAGAMENTO_INFORMADO` | `As formas de pagamento aceitas por {nome} sao: {formasPagamento}.` |
| `CHATBOT_MSG_PAGAMENTO_NAO_CONFIGURADO` | `Ainda nao tenho as formas de pagamento configuradas. Vou encaminhar para a equipe confirmar essa informacao.` |
| `CHATBOT_MSG_ENDERECO_INFORMADO` | `O endereco de {nome} e: {endereco}.` |
| `CHATBOT_MSG_ENDERECO_NAO_CONFIGURADO` | `Ainda nao tenho o endereco configurado. Vou encaminhar para a equipe confirmar essa informacao.` |
| `CHATBOT_MSG_ENTREGA_GENERICA` | `Fazemos verificacao de entrega por regiao. Informe seu CEP para consultarmos a disponibilidade.` |
| `CHATBOT_MSG_ENTREGA_CIDADE_ATENDIDA` | `Entregamos em toda a cidade de {cidade}.` |
| `CHATBOT_MSG_ENTREGA_FORA_AREA` | `No momento, fazemos entrega apenas em: {cidades}.` |
| `CHATBOT_MSG_ENTREGA_SOLICITAR_CIDADE` | `Fazemos entrega nas seguintes cidades: {cidades}. Informe a cidade do endereco para confirmarmos.` |
| `CHATBOT_MSG_ENTREGA_CEP_NAO_VALIDADO` | `Nao consegui validar esse CEP no momento. Informe a cidade do endereco para confirmarmos a entrega.` |

Documentacao Gemini:

- API key: https://aistudio.google.com/app/apikey
- Modelos: https://ai.google.dev/gemini-api/docs/models/gemini
- Structured output: https://ai.google.dev/gemini-api/docs/structured-output

## Executar com PostgreSQL

Crie o banco:

```sql
CREATE DATABASE campus_ia_chatbot;
```

Execute:

```bash
mvn spring-boot:run
```

## Executar em producao

Configure as variaveis obrigatorias:

```bash
GEMINI_API_KEY=
ESTABELECIMENTO_NOME=
ESTABELECIMENTO_TIPO=
ESTABELECIMENTO_HORARIO_FUNCIONAMENTO=
ESTABELECIMENTO_ENDERECO=
ESTABELECIMENTO_FORMAS_PAGAMENTO=
ESTABELECIMENTO_ENTREGA=
ESTABELECIMENTO_CIDADES_ATENDIDAS=
ESTABELECIMENTO_UF=
ESTABELECIMENTO_TELEFONE=
ESTABELECIMENTO_SITE=
DB_URL=
DB_USERNAME=
DB_PASSWORD=
```

Execute com o profile `prod`:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

No profile `prod`, o Hibernate usa `ddl-auto=validate` por padrao. Para alterar explicitamente:

```bash
JPA_DDL_AUTO=validate
```

### Configuracao alvo para Azure App Service

O caminho de configuração para Cosmos e Azure OpenAI usa os perfis `prod,cosmos`. O perfil `cosmos` remove as auto-configurações de DataSource/JPA e espera o endpoint real do Cosmos e identidade gerenciada; o emulador é configurado somente pelo perfil `local`.

```text
SPRING_PROFILES_ACTIVE=prod,cosmos
IA_PROVIDER=AZURE_OPENAI
AZURE_COSMOS_ENDPOINT=https://<conta>.documents.azure.com:443/
AZURE_COSMOS_AUTENTICACAO=MANAGED_IDENTITY
AZURE_COSMOS_CONVERSAS_CONTAINER=conversas
AZURE_COSMOS_CATALOGO_CONTAINER=catalogo
AZURE_COSMOS_ESTABELECIMENTOS_CONTAINER=estabelecimentos
AZURE_COSMOS_CATALOGO_ID=renovo
AZURE_COSMOS_ESTABELECIMENTO_ID=renovo
AZURE_COSMOS_PROVISIONING_ENABLED=false
AZURE_COSMOS_RESET_ON_STARTUP=false
AZURE_COSMOS_SEED_ENABLED=false
AZURE_OPENAI_ENDPOINT=https://<recurso>.openai.azure.com
AZURE_OPENAI_DEPLOYMENT=<deployment>
AZURE_OPENAI_AUTHENTICATION=MANAGED_IDENTITY
```

A identidade do App Service precisa de acesso de dados ao Cosmos e do papel `Cognitive Services OpenAI User` no recurso Azure OpenAI. `AZURE_COSMOS_PROVISIONING_ENABLED` e `AZURE_COSMOS_SEED_ENABLED` permanecem `false` por padrão em produção, para que a identidade da aplicação não crie recursos nem altere dados na inicialização. Provisionamento, atribuição de RBAC, validação contra recursos reais, migração de dados e deploy ainda precisam ser executados em ambiente autorizado.

Para ambientes descartaveis, `AZURE_COSMOS_RESET_ON_STARTUP=true` remove o database configurado antes de recriar os containers. Essa opcao exige tambem `AZURE_COSMOS_PROVISIONING_ENABLED=true` e `AZURE_COSMOS_SEED_ENABLED=true`. Cada inicializacao apaga permanentemente catalogo, estabelecimento, conversas e qualquer outro dado armazenado nesse database. Nao use com mais de uma instancia da aplicacao iniciando ao mesmo tempo.

## Executar localmente com H2

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Console H2:

```text
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:campus_ia_chatbot
User: sa
Password:
```

## Executar localmente com Cosmos DB

O ambiente local usa o emulador oficial Cosmos DB for NoSQL vNext, exposto apenas no loopback. Docker deve estar em execução.

```powershell
docker compose -f infra/cosmos/docker-compose.emulator.yml up -d
./infra/cosmos/export-emulator-truststore.ps1
./infra/cosmos/run-backend.ps1
```

O perfil `local,cosmos` inicia sem DataSource/JPA, usa classificação local e não chama o serviço externo de CEP. Nesse perfil, `AZURE_COSMOS_PROVISIONING_ENABLED` e `AZURE_COSMOS_SEED_ENABLED` assumem `true`; o script `infra/cosmos/run-backend.ps1` também as exporta explicitamente. Assim, o emulador cria os containers e importa os arquivos versionados somente se catalogo e estabelecimento estiverem vazios. O script `infra/cosmos/seed-catalogo.ps1` valida esse fluxo sem sobrescrever dados existentes.

### Testar localmente com o Codex CLI

O Codex CLI pode ser selecionado temporariamente como adaptador de interpretação no perfil `local`. Esse caminho executa um processo local isolado para cada interação, mas a inferência usa um modelo remoto e consome a franquia da conta autenticada no Codex. Ele é independente do adaptador Azure OpenAI.

O aplicativo não instala o CLI nem inicia autenticação. Verifique previamente a instalação e o estado da sessão:

```powershell
codex --version
codex login status
```

Para habilitar o adaptador com o Cosmos local:

```powershell
$env:IA_PROVIDER = "CODEX_CLI"
$env:CODEX_CLI_TIMEOUT = "45s"
$env:CODEX_CLI_MAX_OUTPUT_BYTES = "65536"
./infra/cosmos/run-backend.ps1
```

`CODEX_CLI_MODEL` é opcional. Quando estiver vazio, o CLI usa o modelo configurado para a conta. Cada chamada usa `codex exec` com sessão efêmera, configuração e regras do usuário ignoradas, diretório temporário fora do repositório, sandbox somente leitura, MCP e demais ferramentas desativados. O prompt é enviado por `stdin`, e a resposta deve cumprir o JSON Schema do contrato de interpretação.

Configurações disponíveis:

| Variavel | Padrao | Descricao |
|---|---|---|
| `IA_PROVIDER` | `GEMINI` | Aceita `GEMINI`, `AZURE_OPENAI` ou `CODEX_CLI`; o último é restrito ao perfil `local`. |
| `CODEX_CLI_EXECUTABLE` | `codex` | Nome ou caminho do executável instalado. |
| `CODEX_CLI_MODEL` | _(vazio)_ | Modelo remoto opcional. |
| `CODEX_CLI_TIMEOUT` | `45s` | Limite de duração de uma execução. |
| `CODEX_CLI_MAX_OUTPUT_BYTES` | `65536` | Limite de saída do processo e do JSON final. |

Para desabilitar e voltar ao comportamento padrão:

```powershell
Remove-Item Env:IA_PROVIDER -ErrorAction SilentlyContinue
Remove-Item Env:CODEX_CLI_MODEL -ErrorAction SilentlyContinue
Remove-Item Env:CODEX_CLI_TIMEOUT -ErrorAction SilentlyContinue
Remove-Item Env:CODEX_CLI_MAX_OUTPUT_BYTES -ErrorAction SilentlyContinue
```

Se o processo falhar, exceder o timeout ou produzir JSON inválido, a aplicação registra somente a categoria técnica da falha e usa a classificação local segura, sem repetir a chamada.

Para validar o SDK e a API contra o emulador real:

```powershell
./infra/cosmos/test-emulator.ps1
./infra/cosmos/test-api-emulator.ps1
```

Os arquivos de ambiente e truststore ficam em `.local/cosmos` e não são versionados.

Swagger:

```text
http://localhost:8080/swagger-ui/index.html
http://localhost:8080/v3/api-docs
```

## Executar testes

```bash
mvn test
```

Os testes usam H2 em memoria via perfil `test`.

## Endpoints

### POST /api/portal/chat

Recebe uma mensagem enviada pelo assistente virtual do portal.

Request:

```json
{
  "sessionId": "17261424001234567890",
  "message": "Gostaria de saber se voces fazem entrega."
}
```

Campos do request:

| Campo | Obrigatorio | Descricao |
|---|---|---|
| `sessionId` | Sim | Identificador anonimo com 8 a 20 digitos. Identifica a sessao ativa do portal. |
| `message` | Sim | Mensagem do visitante (ate 4000 caracteres). |

Response:

```json
{
  "idAtendimento": "0198af63-89d2-7e72-bca1-0242ac120002",
  "origem": "PORTAL",
  "mensagemCliente": "Gostaria de saber se voces fazem entrega.",
  "tipoSolicitacao": "ENTREGA",
  "categoria": "ATENDIMENTO_ADMINISTRATIVO",
  "respostaGerada": "Realizamos entregas via Correio e Transportadora para todo o Brasil, e via Motoboy para cidades atendidas. Informe seu CEP para confirmarmos.",
  "necessitaAtendimentoHumano": false,
  "motivoEncaminhamento": null,
  "confianca": 90.0,
  "status": "PROCESSADO",
  "dataProcessamento": "2026-05-23T10:30:00"
}
```

### POST /api/webhook/whatsapp

Recebe uma mensagem enviada pelo canal WhatsApp. Usa o contrato interno com `telefoneCliente`, `nomeCliente`, `mensagem` e `origem`.

Os dois endpoints `POST` aceitam o cabeçalho opcional `Idempotency-Key`, de 1 a 200 caracteres. No backend Cosmos, repetir a mesma chave e payload devolve o resultado persistido; usar a chave com outro payload ou enquanto o processamento anterior está em curso devolve HTTP 409. Um processamento abandonado que excede a expiração é encerrado como falha terminal e não repete a chamada de IA automaticamente.

O CORS de `/api/portal/**` permite por padrao `http://localhost:5500` e `http://127.0.0.1:5500`. Em outros ambientes, configure a lista separada por virgulas em `PORTAL_CORS_ALLOWED_ORIGINS`.

A identidade de conversa remove apenas espaços, parênteses, pontos e hífens do telefone. Os dígitos e um eventual `+` inicial são preservados; não há inclusão ou remoção automática de país ou DDD. Assim, `14999999999` e `(14) 99999-9999` representam o mesmo cliente, enquanto `+5514999999999` continua distinto. Partições locais criadas antes desta regra não são movidas automaticamente.

### GET /api/atendimentos

Lista os atendimentos em um envelope com `itens` e `proximoToken`. Use `tamanho`, cujo padrão é 50 e o máximo é 100, e envie o token opaco retornado na consulta seguinte.

### GET /api/atendimentos/{id}

Consulta um atendimento pelo ID.

### GET /api/atendimentos/status/{status}

Lista atendimentos filtrados por status com os mesmos parâmetros `tamanho` e `token`:

| Status | Descricao |
|---|---|
| `RECEBIDO` | Mensagem recebida, aguardando processamento. |
| `PROCESSADO` | Atendimento respondido automaticamente. |
| `AGUARDANDO_ANALISE_HUMANA` | Encaminhado para atendimento humano. |
| `FINALIZADO` | Atendimento encerrado. |
| `ERRO_PROCESSAMENTO` | Falha durante o processamento. |

## Tipos de solicitacao e categorias

### Tipos de solicitacao (`tipoSolicitacao`)

| Valor | Descricao |
|---|---|
| `ORCAMENTO_FORMULA` | Orcamento de formula manipulada. Sempre encaminhado para humano. |
| `COMPRA_PRODUTO` | Compra de produto pronto do catalogo Renovo. |
| `STATUS_PEDIDO` | Consulta de status de pedido. |
| `ENVIO_RECEITA` | Envio de receita medica. |
| `RECOMPRA` | Recompra de produto ou formula anterior. |
| `HORARIO_FUNCIONAMENTO` | Consulta de horario de funcionamento. |
| `ENTREGA` | Consulta sobre entrega e frete. |
| `FORMAS_PAGAMENTO` | Consulta sobre formas de pagamento. |
| `DUVIDA_ADMINISTRATIVA` | Outras duvidas administrativas. |
| `DUVIDA_FARMACEUTICA` | Duvida clinica ou farmaceutica. Sempre encaminhado para humano. |
| `RECLAMACAO` | Reclamacao. Sempre encaminhado para humano. |
| `OUTROS` | Solicitacoes nao classificadas. |

### Categorias (`categoria`)

| Valor | Descricao |
|---|---|
| `ATENDIMENTO_COMERCIAL` | Comercial (compras, orcamentos, recompras). |
| `ATENDIMENTO_ADMINISTRATIVO` | Administrativo (horario, endereco, entrega, pagamento). |
| `ATENDIMENTO_FARMACEUTICO` | Farmaceutico (duvidas clinicas, receitas). Sempre encaminhado para humano. |
| `RECLAMACAO` | Reclamacoes. Sempre encaminhado para humano. |
| `OUTROS` | Outros. |

## Fluxo de compra de produto do catalogo

Quando o cliente demonstra intencao de comprar um produto do catalogo (palavras como "quero", "gostaria", "comprar", "pedido"):

1. O provedor selecionado solicita uma busca limitada pelo nome informado; o catalogo completo não entra no prompt inicial.
2. Se a busca localizar o produto, uma segunda inferencia confirma somente o nome e o preco retornados e solicita em uma unica mensagem: quantidade, forma de pagamento, modalidade de entrega e endereco (ou confirmacao de retirada).
3. Quando o cliente fornece todas as informacoes ao longo da conversa, o chatbot confirma o resumo e marca `necessitaAtendimentoHumano = true` para que a equipe processe o pedido.
4. A solicitacao e classificada como `COMPRA_PRODUTO` / `ATENDIMENTO_COMERCIAL`.

## Exemplos

### Mensagem sensivel (encaminhamento para humano)

Request:

```json
{
  "telefoneCliente": "14999999999",
  "nomeCliente": "Maria",
  "mensagem": "Qual dose desse medicamento posso dar para uma crianca?",
  "origem": "WHATSAPP"
}
```

Response esperada:

```json
{
  "tipoSolicitacao": "DUVIDA_FARMACEUTICA",
  "categoria": "ATENDIMENTO_FARMACEUTICO",
  "respostaGerada": "Essa orientacao precisa ser avaliada por um farmaceutico ou profissional de saude. Vou encaminhar seu atendimento para analise humana.",
  "necessitaAtendimentoHumano": true,
  "motivoEncaminhamento": "Mensagem envolve orientacao farmaceutica ou clinica.",
  "status": "AGUARDANDO_ANALISE_HUMANA"
}
```

### Consulta de entrega por Motoboy

Request:

```json
{
  "telefoneCliente": "14999999999",
  "mensagem": "Voces entregam de motoboy em Bauru?",
  "origem": "WHATSAPP"
}
```

Response esperada (Bauru nao configurada em `ESTABELECIMENTO_CIDADES_ATENDIDAS`):

```json
{
  "tipoSolicitacao": "ENTREGA",
  "categoria": "ATENDIMENTO_ADMINISTRATIVO",
  "respostaGerada": "Nosso servico de Motoboy atende apenas em: Iacanga, Arealva, Reginopolis. Para outras regioes, oferecemos entrega via Correio ou Transportadora para todo o Brasil.",
  "necessitaAtendimentoHumano": false,
  "status": "PROCESSADO"
}
```
