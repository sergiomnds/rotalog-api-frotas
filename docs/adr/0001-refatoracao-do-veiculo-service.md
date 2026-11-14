# ADR 0001 — Refatoração do `VeiculoService`

- **Status:** Proposta — **nada foi implementado no código**; documento para avaliação
- **Data:** 2026-11-13
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

### 2.3 Riscos que **permanecem** (fora do escopo desta ADR)

| # | Risco | Recomendação |
|---|---|---|
| R1 | **Corrida *check-then-act* na placa:** duas requisições simultâneas passam pela checagem; a segunda falha no `UNIQUE` com `DataIntegrityViolationException`, hoje devolvida como erro genérico. | Tratar `DataIntegrityViolationException` no `@ControllerAdvice` e mapear para `PlacaDuplicadaException` (409). |
| R2 | **Sem `@Version` na entidade:** atualizações concorrentes de quilometragem/status sofrem *lost update*. | Adicionar `@Version` em `Veiculo`. |
| R3 | **Alerta de manutenção dispara a cada atualização** de quilometragem ≥ 50.000 km, não só na primeira vez que o limite é cruzado. | Notificar apenas na transição (`antes < limite ≤ depois`) ou guardar flag de alerta enviado. |
| R4 | **Notificação síncrona, com `RestTemplate` sem timeout** (mapa 3.5). Se a api-notificacoes travar, a requisição de veículo trava junto. | Timeout no `RestTemplate` como *bean*; depois fila/outbox. |
| R5 | **Sem máquina de estados:** `desativar` aceita veículo em `MANUTENCAO`; `ManutencaoService` muda o status do veículo por conta própria, sem coordenação com `VeiculoService`. | Concentrar transições de status em um único ponto, com transições permitidas explícitas no enum. |
| R6 | Controller captura `RuntimeException` e devolve `e.getMessage()` (mapa 3.3); qualquer exceção de infraestrutura vaza texto interno. | `@ControllerAdvice` mapeando `FrotasException` e um *fallback* genérico. Era a razão de usar exceções específicas. |
| R7 | Sem paginação em `listarTodos`; entidade JPA exposta direto ao cliente (mapa 3.1). | DTO de resposta e `Pageable`. |
| R8 | Sem autenticação/autorização (mapa 3.3, T1). | Tratado em ADR própria. |

## 3. Decisão

Dividir a classe por responsabilidade, substituir `String` de status por enum, introduzir exceções específicas e cobrir tudo com testes unitários.

> As seções 3, 4 e 7 descrevem a solução como foi prototipada e medida; o protótipo foi descartado e o código está no estado original. Os tamanhos e números abaixo são estimativas baseadas nele.

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

**Mantido de propósito:** reduzir a quilometragem continua só gerando aviso em log (B5). Transformar em erro é decisão de negócio (correção de odômetro legítima?) e exige confirmação.

## 5. Consequências

**Positivas**
- Cada classe é testável sem Spring e tem um motivo para mudar.
- Erros distinguíveis por tipo; base pronta para o `@ControllerAdvice` (R6).
- Status inválido não compila mais nos fluxos internos.
- Estatísticas passam de 4 consultas que carregam registros para 4 `count`.

**Negativas / custos**
- Mais arquivos (de 1 para 5 serviços e 8 exceções).
- `VeiculoService` ainda depende de 4 colaboradores; é o ponto de orquestração.
- **Risco de dados:** se existir linha em `frotas.veiculos` com `status` fora de `ATIVO/INATIVO/MANUTENCAO` (outro *case*, espaço), o Hibernate passa a falhar ao ler. Antes de publicar, rodar `SELECT DISTINCT status FROM frotas.veiculos;` (os scripts de seed usam os 3 valores válidos).

## 6. Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Manter a classe e só organizar métodos | Não resolve testabilidade nem acoplamento com notificação. |
| `@ControllerAdvice` agora | É o destino natural das exceções, mas muda o contrato HTTP de todos os controllers; fica para uma ADR própria (R6). |
| `@Converter` de status com tolerância a *case* | Esconderia dado sujo em vez de revelá-lo. |
| Eventos de domínio (`ApplicationEventPublisher`) para notificação | Desacopla melhor, mas não resolve R3/R4 sozinho e aumenta o escopo. Considerar junto com fila/outbox. |

## 7. Verificação do protótipo

O protótipo foi compilado e testado com `mvn verify` (JDK 25 local, alvo Java 11): **53 testes, 0 falhas**, e depois descartado a pedido. Isso mostra que a abordagem é viável e que a meta de 90% é atingível; não há código desta ADR no repositório.

| Classe | Linhas | Branches |
|---|---|---|
| `VeiculoService` | 63/63 (100%) | 16/16 |
| `VeiculoValidator` | 19/19 (100%) | 22/22 |
| `VeiculoNotificador` | 21/21 (100%) | — |
| `VeiculoManutencaoService` | 9/9 (100%) | 4/4 |
| `VeiculoEstatisticasService` | 5/5 (100%) | — |
| `StatusVeiculo` | 8/8 (100%) | 4/4 |

Proposta de enforcement: adicionar JaCoCo (0.8.14) ao `pom.xml` com `check` na fase `verify`, falhando o build se qualquer dessas classes ficar abaixo de 90% de linhas ou de branches.

**Não verificado:** a aplicação não foi iniciada e não há teste de integração. Portanto, o mapeamento `@Enumerated` e o JPQL com literal de enum só foram validados por compilação, não contra PostgreSQL. Isso deve ser conferido em ambiente local (`docker-compose` + `mvn spring-boot:run`) antes de publicar. Os controllers não têm testes (cobertura fora do alvo desta ADR).

## 8. Próximos passos sugeridos

1. `@ControllerAdvice` para `FrotasException` (404/400/409) e remover os `try/catch` dos controllers (R6, R1).
2. Timeout no `RestTemplate` e alerta de manutenção apenas na transição (R4, R3).
3. `@Version` em `Veiculo` e transições de status explícitas (R2, R5).
4. Mesmo tratamento para `Manutencao` e `Motorista` (enum de status + exceções específicas).
