# RotaLog API Frotas

Microsserviço de gestão de frotas — veículos, motoristas e manutenções.

## Stack

- Java 11
- Spring Boot 2.7
- Spring Data JPA / Hibernate
- Flyway (migrations)
- PostgreSQL (schema `frotas`)

## Como rodar

Pré-requisito: PostgreSQL rodando via `rotalog-workspace`.

```bash
./mvnw spring-boot:run
```

A API sobe na porta **8080**.

## Estrutura do projeto

```
src/main/java/com/rotalog/frotas/
├── controller/       # Endpoints REST
├── service/          # Regras de negócio
├── repository/       # Acesso a dados (Spring Data)
├── model/            # Entidades JPA
└── config/           # Configurações
```

## Endpoints principais

| Método | Rota                       | Descrição                    |
|--------|----------------------------|------------------------------|
| GET    | /api/veiculos              | Listar veículos              |
| GET    | /api/veiculos/{id}         | Buscar veículo por ID        |
| POST   | /api/veiculos              | Cadastrar veículo            |
| GET    | /api/motoristas            | Listar motoristas            |
| GET    | /api/manutencoes           | Listar manutenções           |
| GET    | /api/alertas-manutencao    | Listar alertas de manutenção preventiva (`?status=PENDENTE\|ENVIADO\|FALHA`) |
| POST   | /api/alertas-manutencao/verificacao    | Verifica os veículos ativos e gera alertas para os elegíveis |
| POST   | /api/alertas-manutencao/reprocessamento | Reenvia os alertas `PENDENTE` |

## Alerta de manutenção preventiva

Um veículo `ATIVO` é elegível quando, desde a última manutenção `CONCLUIDA` (ou, se nunca houve, desde o
cadastro), rodou mais que o limite de km **ou** passou mais que o limite de meses. Os limites ficam em
`application.properties`:

```
rotalog.manutencao.preventiva.limite-km=10000
rotalog.manutencao.preventiva.limite-meses=6
rotalog.notificacao.gestor=gestor@rotalog.com
```

O alerta é gravado em `frotas.alertas_manutencao` (script `10-add-alertas-manutencao.sql` do workspace) como
`PENDENTE` e então enviado por HTTP síncrono para `POST /api/notificacoes/alertas-manutencao` da api-notificacoes:

| Resposta da api-notificacoes | Status do alerta |
|------------------------------|------------------|
| 2xx (e-mail enviado)         | `ENVIADO`        |
| fora do ar, timeout ou 5xx   | `PENDENTE` (reprocessável) |
| 4xx (pedido rejeitado)       | `FALHA`          |

Não é criado novo alerta para o veículo enquanto houver um `PENDENTE` ou `ENVIADO` desde a última manutenção.
A verificação roda sob demanda (não há job agendado).

## Migrations

Flyway em `src/main/resources/db/migration/`. Seguir a sequência existente ao
adicionar novas migrations.
