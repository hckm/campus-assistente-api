# ADR-0001: Azure Cosmos DB, Azure IA e hospedagem no Azure

**Status:** Aceito — implementação local em andamento

**Data:** 2026-09-05

**Responsáveis pela decisão:** mantenedor do campus-ia-chatbot

**Branch:** `feat/azure-cosmos-db-migration`

**Base local inspecionada:** `main`, commit `93bd40a`

## Contexto

O objetivo é migrar a persistência para o Azure Cosmos DB, substituir a integração Gemini por Azure IA e preparar a API para implantação no Azure. O backend atual usa Java 21, Spring Boot 3.4.5, JPA/Hibernate, PostgreSQL no perfil `prod`, H2 nos perfis `local` e `test` e Gemini para interpretação das mensagens.

Existem três entidades relacionais: atendimento, mensagem de atendimento e produto do catálogo Renovo. As mensagens dependem de relacionamento, cascade e transações JPA. A sessão é procurada pelo telefone, status e janela de inatividade. O contrato HTTP expõe IDs `Long` e listagens completas sem paginação. O catálogo é atualizado pelo CSV em cada inicialização e lido integralmente para montar o prompt.

Não foram consultados bancos existentes nem uma assinatura Azure. Volume, orçamento, região, retenção e necessidade de preservar dados continuam pendentes. A proposta inicial pressupõe uma aplicação de pequeno porte, um estabelecimento e uma região; essas hipóteses precisam ser verificadas antes do provisionamento.

## Decisão proposta

1. Usar **Azure Cosmos DB for NoSQL**, com documentos JSON e modelagem orientada às consultas da aplicação.
2. Usar **Azure Cosmos DB Java SDK v4** (`com.azure:azure-cosmos`) e `azure-identity`, encapsulados por interfaces de persistência da aplicação. Preferir a API síncrona, coerente com o Spring MVC atual. O SDK oferece APIs síncrona e assíncrona no mesmo artefato. [Documentação do SDK](https://learn.microsoft.com/en-us/azure/cosmos-db/sdk-java-v4).
3. Colocar resumos de atendimento, mensagens individuais e controle de sessão no mesmo container `conversas`, particionado por `/clienteChave`. A proposta inicial usa o telefone normalizado como chave, preserva o valor original em campo próprio e proíbe sua exposição em logs de diagnóstico. Isso acompanha a identificação de cliente já adotada pela aplicação; não constitui autenticação.
4. Usar um container separado `catalogo`, particionado por `/catalogoId`, inicialmente `renovo`, com IDs determinísticos por código do produto. Essa concentração é aceitável para o catálogo pequeno observado; seu crescimento exige reavaliar a chave.
5. Manter cada mensagem em documento próprio. Não embutir um histórico crescente no documento de atendimento. A chave deve ser validada antes de importar dados: partições lógicas têm limite de armazenamento e a distribuição influencia o custo das consultas. [Particionamento](https://learn.microsoft.com/en-us/azure/cosmos-db/partitioning-overview).
6. Substituir a atomicidade de cascade/JPA por **transactional batch** e concorrência otimista com `_etag`. O batch adotado opera dentro do mesmo container e da mesma partição; o plano não depende de funcionalidades em preview. [Transações em lote](https://learn.microsoft.com/en-us/azure/cosmos-db/transactional-batch).
7. Hospedar o JAR em **Azure App Service Linux, Java SE**, validando a disponibilidade do runtime Java 21 na região escolhida. É o caminho proposto por aproveitar o executável Spring Boot existente sem introduzir contêiner na primeira implantação. [Implantação Java](https://learn.microsoft.com/en-us/azure/app-service/configure-language-java-deploy-run).
8. Autenticar o backend no Cosmos com identidade gerenciada e RBAC de dados. Preferir também identidade gerenciada para Azure IA, conforme o serviço/modelo escolhido; reservar Key Vault para segredos que ainda forem necessários. Permissão para administrar o recurso Azure e permissão para ler/escrever documentos são separadas. [RBAC do Cosmos](https://learn.microsoft.com/en-us/azure/cosmos-db/how-to-connect-role-based-access-control) e [referências ao Key Vault](https://learn.microsoft.com/en-us/azure/app-service/app-service-key-vault-references).
9. Avaliar **serverless** para desenvolvimento e primeira carga de uso intermitente; decidir produção após medir RUs e confirmar requisitos de disponibilidade. Serverless cobra operações consumidas e armazenamento e opera em uma região. Carga sustentada ou necessidade de várias regiões favorece throughput provisionado/autoscale. [Comparação de capacidade](https://learn.microsoft.com/en-us/azure/cosmos-db/throughput-serverless).

10. Usar **Azure OpenAI no Microsoft Foundry** para interpretação e geração das respostas em substituição ao Gemini. O adaptador usa Chat Completions API v1, envia o nome configurável do deployment no campo `model` e exige Structured Outputs com JSON Schema estrito. O modelo e o nome do deployment não ficam fixos no código. O endpoint aceito é o endpoint de inferência Azure OpenAI `*.openai.azure.com`, não o endpoint de projeto Foundry `*.services.ai.azure.com/api/projects/*`. [Structured Outputs](https://learn.microsoft.com/en-us/azure/foundry/openai/how-to/structured-outputs) e [API v1](https://learn.microsoft.com/en-us/azure/foundry/openai/latest).
11. Autenticar o Azure OpenAI no App Service com `ManagedIdentityCredential`, usando identidade atribuída pelo sistema ou o client ID explícito de uma identidade atribuída pelo usuário. A identidade precisa do papel `Cognitive Services OpenAI User` no recurso. A opção de chave existe apenas quando selecionada explicitamente e recebe o segredo por variável de ambiente. O caminho de produção não usa fallback para login pessoal. [Identidade gerenciada para Java](https://learn.microsoft.com/en-us/azure/developer/java/sdk/authentication/azure-hosted-apps).
12. Não incluir linhas de produto no prompt inicial. Para mensagens de produto, a primeira inferência pode planejar uma única busca interna por categoria, nome ou faixa de preço. O backend valida e limita essa consulta no banco; somente resultados encontrados podem entrar em uma segunda e última inferência. Pedidos genéricos listam metadados limitados de categorias para solicitar refinamento.

## Opções consideradas

| Opção | Complexidade | Custo e escala | Adequação ao projeto |
|---|---|---|---|
| Cosmos for NoSQL + SDK Java v4 | Média: novos documentos, consultas e transações explícitas | Depende de RUs, índices, distribuição e padrão de acesso | **Proposta:** controle direto sobre batch, partição e paginação |
| Cosmos for NoSQL + Spring Data Cosmos | Média: repositórios familiares, mas exige validar mapeamento e transações | Mesmo serviço e modelo de custo | Viável; não equivale a JPA nem elimina o redesenho. Exige compatibilidade entre Spring Boot e biblioteca |
| Cosmos com API MongoDB | Média/alta: troca de JPA por outro modelo de persistência | Depende da oferta escolhida e do dimensionamento | Não há código MongoDB a reaproveitar; não oferece vantagem demonstrada neste repositório |
| Azure Database for PostgreSQL | Baixa em mudanças de aplicação | Banco relacional gerenciado; dimensionamento próprio | Referência de menor esforço para hospedagem, mas não atende ao objetivo de adotar Cosmos |

Caso se opte por Spring Data Cosmos, escolher a versão pela matriz oficial antes de alterar o `pom.xml`. [Suporte Spring Data Cosmos](https://learn.microsoft.com/en-us/azure/cosmos-db/sdk-java-spring-data-v5).

## Trade-offs e consequências

- As gravações de uma interação ficam próximas e podem ser atômicas no banco; as chamadas HTTP ao Azure IA e ViaCEP continuam fora da transação.
- O adaptador Azure OpenAI preserva mensagens multi-turn e valida novamente a saída estruturada. Recusa, filtro de conteúdo, truncamento, resposta inválida, timeout, 401, 403, 429 e 5xx acionam o classificador local seguro sem repetição automática. Modelo, quotas, região, filtros e custo de tokens ainda precisam ser validados antes do deploy; o Gemini deixa de ser dependência da configuração alvo.
- A chave por cliente facilita sessão e histórico, mas consultas administrativas por status ou somente por ID atravessam partições. Devem ser paginadas, medidas e restritas a usuários autorizados.
- O contrato passa a tratar IDs como strings. É uma mudança incompatível de tipo JSON e exige atualização coordenada dos consumidores ou uma API versionada.
- O domínio deixa de depender de entidades JPA. A transição mantém adaptadores separados para comparação; a persistência definitiva não terá joins, cascade ou geração SQL de IDs.
- Normalização de telefone pode unir registros hoje tratados como distintos. Colisões e números ambíguos precisam de relatório e resolução antes da carga.
- H2 continua útil para testar o adaptador antigo, mas não comprova comportamento do Cosmos. Consultas, batch, concorrência e custo serão validados com o Cosmos.
- TTL de documento não representa a janela de inatividade da conversa. Retenção de dados será decidida separadamente e não haverá exclusão automática na primeira carga.
- A disponibilidade do App Service, do Cosmos e das APIs externas deve ser considerada em conjunto; serverless não significa que toda a hospedagem não tenha custo fixo.

## Itens de ação

- [ ] Confirmar premissas de dados, contrato e ambiente descritas no plano.
- [x] Implementar a prova de conceito local de batch, consultas, concorrência e serialização.
- [x] Refatorar a persistência e executar os testes locais de contrato.
- [x] Implementar o adaptador Azure OpenAI e validar localmente requisição, autenticação, respostas estruturadas, contexto e falhas do provedor.
- [ ] Validar qualidade, RBAC, filtros e comportamento no deployment Azure OpenAI de homologação.
- [ ] Preparar infraestrutura reproduzível e ensaio de migração/retorno.
- [ ] Realizar a implantação conforme os critérios de aceite.

Sequência completa, arquivos afetados e procedimento operacional: [plano de migração](../plano-migracao-azure-cosmos-db.md).
