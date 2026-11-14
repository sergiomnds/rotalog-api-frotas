# ADR 0001 — Refatoração do `VeiculoService`

- **Status:** Implementada — bugs e problemas de design (2.1, 2.2) corrigidos; riscos R1–R7 (2.3) corrigidos ou mitigados; **R8 (autenticação) segue em aberto**. Pendente de validação contra PostgreSQL e de aplicar a migration da coluna `versao` antes de subir a nova versão.
- **Data:** 2026-11-13 (proposta, implementação das seções 2.1/2.2 e tratamento dos riscos 2.3)
- **Escopo:** `rotalog-api-frotas`
- **Origem:** `MAPA_DIVIDAS_TECNICAS.md`, seção 3 (itens 3.1, 3.2, 3.3 e 3.5)

## 1. Contexto

O `VeiculoService` tinha 339 linhas e concentrava validação, regras de manutenção, cálculo de custo, notificações, estatísticas e persistência. O mapa de dívidas já apontava a classe como *god class* (3.1), com `RuntimeException` genérica em ~15 pontos (3.2), `System.out.println` misturado com `@Slf4j` (3.2), status como `String` (3.1) e ausência total de testes (3.5).

Uma leitura linha a linha da classe encontrou, além do que o mapa já registrava, bugs reais e riscos que ele não cobre.

## 2. Problemas encontrados no `VeiculoService` original

### 2.1 Bugs confirmados

| # | Problema | Onde (original) | Efeito |
|---|---|---|---|
| B1 | **Duplicidade de placa é *case-sensitive*:** a consulta usa a placa como veio, mas o registro grava em maiúsculas. | `registrarVeiculo` L78 vs L95 | `abc1234` passa pela checagem mesmo existindo `ABC1234`. Só o `UNIQUE` do banco impede, e vira erro 500/400 genérico. |
| B2 | **NPE com `quilometragem` nula na entidade** (coluna sem `NOT NULL`). | `atualizarVeiculo` L138, `atualizarQuilometragem` L163 | `NullPointerException` ao desempacotar `Long`. |
| B3 | **NPE com entrada nula:** `novaQuilometragem == null` e `status == null`. | L159, L195 | NPE em vez de erro de validação. |
| B4 | **`atualizarVeiculo` não valida nada:** aceita ano 3000 e quilometragem negativa. | L128–148 | Dado inválido chega ao banco (mapa 3.3 já cita o sintoma). |
| B5 | **Redução de quilometragem só gera log**, nos dois métodos, e um deles usa `System.out`. | L138–142, L163–165 | Odômetro pode ser "voltado" sem erro. |

### 2.2 Problemas de design

| # | Problema | Observação |
|---|---|---|
| D1 | God class com 5 responsabilidades | Viola SRP; difícil testar e alterar. |
| D2 | 9 `throw new RuntimeException(...)` | Impossível distinguir "não encontrado" de "dado inválido" sem comparar texto. |
| D3 | Status como `String` em 3 *whitelists* manuais e literais espalhados | `"ATIVO"`, `"INATIVO"`, `"MANUTENCAO"` repetidos em `VeiculoService`, `ManutencaoService` e no JPQL do repositório. |
| D4 | 3× `System.out.println` (L116, L164, L327) | Contornam logger e nível de log. |
| D5 | Código morto: variáveis `intervaloQuilometragem` e `intervaloMeses` (L212–213), parâmetro `modelo` de `calcularCustoManutencao`, e `sincronizarComSistemaExterno` (só imprime) | Nenhum chamador. |
| D6 | `@Autowired` em campo | Impede instanciar a classe sem Spring; foi o que bloqueava testes unitários simples. |
| D7 | `Double` para dinheiro em `calcularCustoManutencao` | Erro de ponto flutuante; o resto do projeto usa `BigDecimal`. |
| D8 | E-mail `gestor@rotalog.com` hardcoded em 4 pontos | Não configurável por ambiente. |
| D9 | `try/catch` de notificação copiado 4× com tratamentos diferentes | Um deles (`registrarVeiculo`) ainda escrevia em `System.out`. |
| D10 | `precisaDeManutencao(Long id)` recarrega o veículo do banco logo após ele ter sido salvo | Consulta extra a cada atualização de quilometragem. |
| D11 | Estatísticas: 3 `findByStatus(...).size()` carregam todos os veículos para contar; JSON montado com `String.format`; método com typo (`Freita`) | O JSON manual quebra se um valor mudar de tipo e não tem contrato. |
| D12 | Logs misturando português e inglês | Dificulta busca em log. |

### 2.3 Riscos que permaneciam após a refatoração das seções 2.1/2.2

Esta seção foi escrita antes de qualquer correção. Depois que 2.1/2.2 foram implementadas, cada risco foi **reproduzido contra o código refatorado** com um teste temporário (já removido): os sete primeiros (R1–R7) se confirmaram, ou seja, a refatoração por si só **não** os resolvia. R8 foi confirmado por inspeção (`pom.xml` sem Spring Security). Em seguida foram tratados; a coluna "Situação" é o resultado.

| # | Risco | Situação | Tratamento |
|---|---|---|---|
| R1 | **Corrida *check-then-act* na placa:** duas requisições simultâneas passam pela checagem; a segunda falha no `UNIQUE` com `DataIntegrityViolationException`, devolvida como erro genérico. | **Corrigido** | `VeiculoService.registrarVeiculo` converte `DataIntegrityViolationException` do `save` em `PlacaDuplicadaException`, que o `GlobalExceptionHandler` traduz para **409**. A checagem prévia continua (dá a resposta rápida no caso comum); a restrição `UNIQUE` do banco segue sendo a garantia real. |
| R2 | **Sem `@Version` na entidade:** atualizações concorrentes de quilometragem/status sofrem *lost update*. | **Mitigado** | `Veiculo.versao` com `@Version` (coluna `versao BIGINT NOT NULL DEFAULT 0`); conflito vira `ObjectOptimisticLockingFailureException` → **409**. Limite: protege a janela de leitura-e-gravação **dentro do servidor** (ex.: `ManutencaoService` × `VeiculoService`). Uma edição baseada em dado velho do *cliente* só seria detectada se a versão fosse enviada de volta (`If-Match`/campo `versao`), o que não foi feito (a resposta não expõe `versao`). |
| R3 | **Alerta de manutenção dispara a cada atualização** de quilometragem ≥ 50.000 km. | **Corrigido** | `VeiculoManutencaoService.cruzouLimite(anterior, atual)`: notifica só na transição (`anterior < limite ≤ atual`; leitura anterior nula conta como abaixo). Sem *flag* persistida: se a quilometragem for reduzida abaixo do limite e subir de novo, o alerta é reenviado, o que é desejável. |
| R4 | **Notificação síncrona, com `RestTemplate` sem timeout** (mapa 3.5). Se a api-notificacoes travar, a requisição de veículo trava junto. | **Mitigado** | `RestTemplate` dedicado como *bean* (`HttpClientConfig`), com timeouts de conexão (2 s) e leitura (5 s) configuráveis; URL em `rotalog.api.notificacoes.url`. Continua **síncrono**: no pior caso a requisição espera até ~7 s. Fila/*outbox* continua recomendada e **não foi feita**. |
| R5 | **Sem máquina de estados:** `desativar` aceita veículo em `MANUTENCAO`; `ManutencaoService` muda o status por conta própria. | **Corrigido** | `StatusVeiculo.podeTransitarPara` define as transições (ver abaixo) e `VeiculoService.alterarStatus` é o ponto único que valida e grava; `desativar`, `reativar` e os três pontos do `ManutencaoService` passam por ele. Transição inválida → `TransicaoStatusInvalidaException` → **409**. |
| R6 | Controller captura `RuntimeException` e devolve `e.getMessage()`; exceção de infraestrutura vaza texto interno e vira 404/400. | **Corrigido para `VeiculoController`** | `GlobalExceptionHandler` (`@RestControllerAdvice`, formato `{"erro": "..."}` mantido): `VeiculoNaoEncontradoException` 404; `PlacaDuplicada`/`TransicaoStatusInvalida` 409; demais `FrotasException` 400; `DataIntegrityViolation` e conflito de concorrência 409 sem detalhes; qualquer outra exceção → 500 com mensagem genérica e detalhe só no log. Estende `ResponseEntityExceptionHandler`, então erros do Spring MVC (JSON inválido, método não suportado) mantêm o status correto no mesmo formato. Os `try/catch` do `VeiculoController` foram removidos. **`ManutencaoController` e `MotoristaController` ainda têm os próprios `try/catch`** (fora do escopo) e continuam expondo `e.getMessage()`. |
| R7 | Sem paginação em `listarTodos`; entidade JPA exposta direto ao cliente (mapa 3.1). | **Corrigido para veículos** | `VeiculoResponse` (mesmos campos de antes, sem `versao`); `GET /veiculos` e `GET /veiculos/status/{status}` aceitam `?page&size&sort` (padrão 100, máximo 500, ordenação por `id`). O corpo **continua sendo um array** para não quebrar o painel-admin; o total vem nos headers `X-Total-Count` e `X-Total-Pages`. Entidades de `Manutencao` e `Motorista` continuam expostas. |
| R8 | Sem autenticação/autorização (mapa 3.3, T1). | **Em aberto** | Exige decisões que não cabem aqui (provedor de identidade, papéis, tokens, impacto nos 3 consumidores). Mantida para ADR própria; nenhuma mudança foi feita. |

**Transições de status adotadas (R5):** `ATIVO → INATIVO | MANUTENCAO`, `INATIVO → ATIVO | MANUTENCAO`, `MANUTENCAO → ATIVO`. Repetir o status atual é idempotente (sem gravação nem notificação). A única transição que passou a ser recusada é `MANUTENCAO → INATIVO`. `INATIVO → MANUTENCAO` foi mantida porque o fluxo atual de manutenção já a permitia; se for indesejada, basta alterar `StatusVeiculo.podeTransitarPara`.

## 3. Decisão

Dividir a classe por responsabilidade, substituir `String` de status por enum, introduzir exceções específicas e cobrir tudo com testes unitários.

> **Implementado em 2026-11-13.** Os bugs B1–B5 e os problemas D1–D12 da seção 2.1/2.2 foram tratados conforme as seções 3 e 4. Os tamanhos de classe da tabela 3.1 são as estimativas do protótipo; o código final pode diferir ligeiramente.

### Situação por item da seção 2

| Item | Situação |
|---|---|
| B1 placa *case-sensitive* | Corrigido (`VeiculoValidator.normalizarPlaca/padronizarPlaca`) |
| B2, B3 NPE com valores nulos | Corrigido (exceções de validação) |
| B4 `atualizarVeiculo` sem validação | Corrigido |
| B5 redução de quilometragem | **Mantido de propósito** (só aviso em log; decisão de negócio pendente) |
| D1 god class, D6 `@Autowired` em campo | Corrigido (5 serviços, injeção por construtor no service e no `VeiculoController`) |
| D2 `RuntimeException` genérica | Corrigido (9 exceções sob `FrotasException` (a 9ª, `TransicaoStatusInvalidaException`, veio com R5)) |
| D3 status como `String` | Corrigido para `Veiculo` (enum `StatusVeiculo`); enums de `Manutencao` e `Motorista` fora do escopo |
| D4, D12 `System.out` e logs mistos | Corrigido |
| D5 código morto | Removido |
| D7 `Double` para dinheiro | Corrigido (`BigDecimal`) |
| D8 e-mail hardcoded | Corrigido (`rotalog.notificacao.gestor`) |
| D9 `try/catch` de notificação duplicado | Corrigido (`VeiculoNotificador`) |
| D10 recarga desnecessária do veículo | Corrigido |
| D11 estatísticas | Corrigido (`countByStatus` + `EstatisticasFrotaResponse`) |

### 3.1 Estrutura proposta

| Classe | Responsabilidade | Linhas (protótipo) |
|---|---|---|
| `VeiculoService` | Casos de uso de veículo (CRUD, quilometragem, ativar/desativar); orquestra os demais | ~133 (hoje 339) |
| `VeiculoValidator` | Validação e padronização de placa, modelo, ano e quilometragem | ~51 |
| `VeiculoNotificador` | Monta e envia notificações de veículo; falha de envio é logada e não interrompe o fluxo | ~56 |
| `VeiculoManutencaoService` | Regra de "precisa de manutenção", custo estimado, agendamento preventivo | ~34 |
| `VeiculoEstatisticasService` | Estatísticas da frota via `count` no banco | ~22 |
| `StatusVeiculo` (enum) | `ATIVO`, `INATIVO`, `MANUTENCAO` + `from(String)` estrito | — |
| `EstatisticasFrotaResponse` | DTO tipado da resposta de estatísticas | — |

### 3.2 Exceções específicas (`com.rotalog.exception`)

Todas estendem `FrotasException` (abstrata, `RuntimeException`), o que **mantém a compatibilidade** com o `catch (RuntimeException)` dos controllers e permite um único `@ControllerAdvice` por tipo base depois.

`VeiculoNaoEncontradoException` (`porId`, `porPlaca`), `PlacaInvalidaException`, `PlacaDuplicadaException`, `ModeloInvalidoException`, `AnoFabricacaoInvalidoException`, `QuilometragemInvalidaException`, `StatusVeiculoInvalidoException`. As mensagens são idênticas às originais.

### 3.3 Enum de status

`Veiculo.status` passaria a `@Enumerated(EnumType.STRING)`, então **nenhuma migration seria necessária**: a coluna continua `VARCHAR` com os mesmos valores. `VeiculoRepository` passaria a usar `StatusVeiculo` (e ganharia `countByStatus`), o JPQL usaria o literal qualificado do enum, e `ManutencaoService` precisaria ser ajustado nos 3 pontos que alteram o status do veículo. Os enums de status de `Manutencao` e `Motorista` ficam **fora** do escopo.

### 3.4 Limpeza

- Remover o javadoc em excesso (e os comentários `FIXME/TODO` que as mudanças resolvem).
- Remover os 3 `System.out.println`; todos os logs em português via `@Slf4j`.
- Remover `intervaloQuilometragem`, `intervaloMeses`, o parâmetro `modelo` de `calcularCustoManutencao` e o método-esqueleto `sincronizarComSistemaExterno`.
- Injeção por construtor (`@RequiredArgsConstructor`) no service e no `VeiculoController`.
- Destinatário das notificações configurável: `rotalog.notificacao.gestor` (padrão `gestor@rotalog.com`, o valor atual).

## 4. Mudanças de comportamento previstas

Quem consome a API ou chama esses métodos deve revisar:

| Mudança | Detalhe |
|---|---|
| **Placa padronizada** | `trim` + maiúsculas antes de validar, buscar e checar duplicidade (corrige B1). `buscarPorPlaca` também padroniza. |
| **Ordem de validação** | Placa, modelo e ano são validados **antes** de consultar duplicidade. |
| **`PUT /veiculos/{id}`** | Passa a rejeitar ano fora de 1900–2100 e quilometragem negativa (400), em vez de gravar (corrige B4). |
| **`PATCH …/quilometragem`** | Quilometragem nula → `QuilometragemInvalidaException` em vez de NPE. |
| **`GET /veiculos/estatisticas`** | Mesmos campos (`total`, `ativos`, `inativos`, `em_manutencao`), mas o `Content-Type` passa de `text/plain` para `application/json`. |
| **Assinaturas removidas ou alteradas** (sem chamadores no repositório) | `obterEstatisticasFreita()` → `VeiculoEstatisticasService.obterEstatisticas()`; `calcularCustoManutencao(String, Long): Double` → `(Long): BigDecimal`; `precisaDeManutencao(Long)` → `(Veiculo)`; `agendarManutencaoPreventiva(Long, Long)` → `(Veiculo, Long)`; `sincronizarComSistemaExterno()` removido. |

Mudanças adicionais decorrentes do tratamento dos riscos (2.3):

| Mudança | Detalhe |
|---|---|
| **Códigos HTTP do `VeiculoController`** | Passam a depender do tipo do erro, não do endpoint: não encontrado → 404 (inclusive em `PUT` e `PATCH …/quilometragem`, antes 400); placa duplicada → **409** (antes 400); transição de status inválida → **409**; erro inesperado → **500** com `{"erro": "Erro interno do servidor"}` (antes 404/400 com a mensagem da exceção). |
| **`PATCH …/quilometragem` sem o campo** | A mensagem passa de `Campo 'quilometragem' é obrigatório` para `Quilometragem é obrigatória` (continua 400). |
| **`PATCH …/desativar` em veículo em manutenção** | Passa a ser recusado com 409. Desativar um veículo já inativo passa a ser no-op (200, sem notificação). |
| **`GET /veiculos` e `GET /veiculos/status/{status}`** | Retornam no máximo 100 itens por padrão (500 no máximo). Quem precisa de mais deve paginar com `?page=` e ler `X-Total-Count`. O painel-admin (`frotas.service.ts`, dashboard) hoje lê só a primeira resposta e usa `length` como total; **passa a subcontar acima de 100 veículos** até ser ajustado para usar `X-Total-Count` ou `/veiculos/estatisticas`. |
| **Corpo das respostas de veículo** | Mesmos campos, porém via `VeiculoResponse`; `versao` não é exposta. |
| **Alerta de manutenção** | Enviado apenas ao cruzar 50.000 km, não a cada atualização acima do limite. |
| **Assinaturas internas alteradas** | `VeiculoService.listarTodos()` → `listarTodos(Pageable)`; `obterVeiculosPorStatus(String)` → `(String, Pageable)` retornando `Page`; `VeiculoRepository.findByStatus` passa a receber `Pageable`; novo `VeiculoService.alterarStatus(Veiculo, StatusVeiculo)`; `NotificacaoClient` agora recebe `RestTemplate` e URL no construtor. |
| **Banco de dados** | Nova coluna obrigatória `frotas.veiculos.versao` (ver seção 5). |

**Mantido de propósito:** reduzir a quilometragem continua só gerando aviso em log (B5). Transformar em erro é decisão de negócio (correção de odômetro legítima?) e exige confirmação.

## 5. Consequências

**Positivas**
- Cada classe é testável sem Spring e tem um motivo para mudar.
- Erros distinguíveis por tipo e traduzidos centralmente em HTTP (R6); infraestrutura não vaza mensagem interna.
- Status do veículo muda por um único ponto, com transições explícitas (R5); alerta de manutenção sem repetição (R3); placa duplicada coerente mesmo sob concorrência (R1).
- Status inválido não compila mais nos fluxos internos.
- Estatísticas passam de 4 consultas que carregam registros para 4 `count`.

**Negativas / custos**
- Mais arquivos (de 1 para 5 serviços e 9 exceções).
- `VeiculoService` ainda depende de 4 colaboradores; é o ponto de orquestração.
- **Migration obrigatória antes do deploy (R2):** a coluna `versao` precisa existir, pois `spring.jpa.hibernate.ddl-auto=validate` e o Flyway está desligado; sem ela a aplicação **não sobe**. Foi criado `rotalog-workspace/tools/scripts/09-add-versao-veiculos.sql` (idempotente, registrado no `docker-compose.yml`) e `V3__Add_Versao_Veiculos.sql` no Flyway da api-frotas. Os scripts de init só rodam na **primeira** criação do volume: em bancos existentes o script 09 deve ser executado manualmente.
- `VeiculoController` passa a depender de `GlobalExceptionHandler` para traduzir erros; `ManutencaoController`/`MotoristaController` seguem com tratamento manual, deixando a API com dois padrões até serem migrados.
- Notificação segue síncrona (R4): timeouts limitam o dano, mas não o eliminam.
- **Risco de dados:** se existir linha em `frotas.veiculos` com `status` fora de `ATIVO/INATIVO/MANUTENCAO` (outro *case*, espaço), o Hibernate passa a falhar ao ler. Antes de publicar, rodar `SELECT DISTINCT status FROM frotas.veiculos;` (os scripts de seed usam os 3 valores válidos).

## 6. Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Manter a classe e só organizar métodos | Não resolve testabilidade nem acoplamento com notificação. |
| `@ControllerAdvice` na primeira etapa | Foi adiado por mudar o contrato HTTP; **acabou feito na segunda etapa** (R6), restrito ao `VeiculoController` na prática, já que os demais controllers ainda capturam as exceções por conta própria. |
| Paginação com corpo `Page<…>` (`content`, `totalElements`…) | Quebraria o painel-admin, que espera um array. Optou-se por manter o array e expor o total em headers. |
| Fila/*outbox* para notificações já no R4 | Resolve de fato a dependência síncrona, mas é uma mudança de arquitetura; ficou como próximo passo, com timeouts como mitigação. |
| `@Converter` de status com tolerância a *case* | Esconderia dado sujo em vez de revelá-lo. |
| Eventos de domínio (`ApplicationEventPublisher`) para notificação | Desacopla melhor, mas não resolve R3/R4 sozinho e aumenta o escopo. Considerar junto com fila/outbox. |

## 7. Verificação

Após a implementação das seções 2.1/2.2, `mvn test` (JDK 25 local, alvo Java 11) executou 68 testes. Após o tratamento dos riscos 2.3, executa **106 testes, 0 falhas**. Cobertura medida pelo JaCoCo:

| Classe | Linhas | Branches |
|---|---|---|
| `VeiculoService` | 77/77 (100%) | 24/24 |
| `VeiculoValidator` | 19/19 (100%) | 22/22 |
| `VeiculoNotificador` | 21/21 (100%) | — |
| `VeiculoManutencaoService` | 12/12 (100%) | 12/12 |
| `VeiculoEstatisticasService` | 5/5 (100%) | — |
| `StatusVeiculo` | 10/10 (100%) | 12/12 |
| `GlobalExceptionHandler` | 14/14 (100%) | — |
| `VeiculoController` | 22/22 (100%) | — |
| `VeiculoResponse` | 9/9 (100%) | — |
| `NotificacaoClient` | 36/36 (100%) | — |
| `HttpClientConfig` | 5/5 (100%) | — |

Novos testes dos riscos: `VeiculoServiceTest` (R1, R3, R5), `VeiculoControllerTest` (R6, R7, via MockMvc standalone com o advice), `NotificacaoClientTest` e `HttpClientConfigTest` (R4), `VeiculoMapeamentoTest` (R2, metadados de mapeamento), `StatusVeiculoTest` (transições), `ManutencaoServiceStatusVeiculoTest` (ponto único de status). `ManutencaoService` (36/67 linhas) e os demais controllers/serviços fora de `Veiculo*` continuam com cobertura parcial ou nula, fora do alvo desta ADR.

Mudanças de build feitas junto: JaCoCo 0.8.13 adicionado ao `pom.xml` (relatório na fase `test`) e Surefire atualizado de 2.22.1 para 3.2.5, pois a versão antiga não executava classes `@Nested`.

**Pendente:** enforcement no build (`check` do JaCoCo na fase `verify`, falhando abaixo de 90% de linhas ou branches) ainda não foi adicionado.

**Não verificado:** a aplicação não foi iniciada e não há teste de integração. Só foram validados por teste unitário/compilação, **não contra PostgreSQL**: o mapeamento `@Enumerated`, o JPQL com literal de enum, `@Version` + coluna `versao` (com `ddl-auto=validate`), os métodos derivados paginados (`findByStatus(…, Pageable)`, `countByStatus`), a conversão real de `DataIntegrityViolationException` na corrida de placa (R1 foi testado com *mock* do repositório) e o comportamento real dos timeouts contra um servidor lento (R4 verifica a configuração do `RestTemplate`, não uma chamada travada). Conferir em ambiente local (`docker-compose` + `mvn spring-boot:run`), aplicar o script 09 e rodar `SELECT DISTINCT status FROM frotas.veiculos;` antes de publicar.

## 8. Próximos passos sugeridos

Itens já feitos (R1–R7, exceto o que está listado abaixo) saíram desta lista. Em aberto:

1. Validar a implementação contra PostgreSQL e adicionar o `check` do JaCoCo na fase `verify` (falhar abaixo de 90% de linhas/branches nas classes `Veiculo*`).
2. Ajustar o painel-admin para o limite de 100 itens: usar `X-Total-Count` e/ou `GET /veiculos/estatisticas` para totais.
3. Enviar `versao` ao cliente (ou `ETag`/`If-Match`) se for necessário detectar edição sobre dado velho do navegador (complemento de R2).
4. Fila/*outbox* para notificações, eliminando a espera síncrona (complemento de R4).
5. Migrar `ManutencaoController` e `MotoristaController` para o `GlobalExceptionHandler` (R6) e dar às entidades `Manutencao` e `Motorista` o mesmo tratamento: DTO de resposta, paginação (R7), enum de status e exceções específicas.
6. **R8:** ADR própria de autenticação/autorização.
