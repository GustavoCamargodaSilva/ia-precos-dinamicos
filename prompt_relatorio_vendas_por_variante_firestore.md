# Prompt (.md) — Relatório de Vendas por Variante de Preço (A/B/C) usando Firestore

**Objetivo:** criar uma forma simples de analisar suas decisões de precificação dinâmica e responder:
- Quantas compras foram com **preço menor** (variante A)
- Quantas compras foram com **preço normal** (variante B)
- Quantas compras foram com **preço maior** (variante C)

Além disso, (opcional mas recomendado) retornar:
- quantas vezes cada variante foi exibida (`shows`)
- taxa de compra por variante
- receita por variante (se você tiver `value_cents` em algum lugar)

Este prompt assume que você tem no Firestore **uma destas estruturas**:

- **Estrutura 1 (recomendada):** coleção `price_impressions` com campos:
  - `variant_id` (A|B|C)
  - `price_cents`
  - `shown_at` (timestamp)
  - `attributed_purchase` (boolean)
  - (opcional) `purchase_value_cents` (number) ou referência para purchase

OU

- **Estrutura 2:** coleção `purchases` contendo:
  - `variant_id`
  - `price_cents` ou `value_cents`
  - `purchased_at`

> Se você tiver as duas, use a Estrutura 1 para contagem e atribuição (mais consistente) e a 2 para receita.

---

## Convenções

Mapeamento:
- `A` = **preço menor** (-10%)
- `B` = **preço normal**
- `C` = **preço maior** (+10%)

Período do relatório:
- padrão: **últimos 30 dias**
- permitir filtros: `start_ms` e `end_ms` (epoch ms)

---

## Saída esperada (JSON)

Um endpoint (Cloud Function) `getSalesVariantSummary` deve retornar algo como:

```json
{
  "window": {"start_ms": 0, "end_ms": 0},
  "purchases": {
    "A": 3,
    "B": 5,
    "C": 2
  },
  "shows": {
    "A": 40,
    "B": 42,
    "C": 38
  },
  "purchase_rate": {
    "A": 0.075,
    "B": 0.119,
    "C": 0.053
  },
  "revenue_cents": {
    "A": 120000,
    "B": 220000,
    "C": 150000
  }
}
```

Se você não tiver receita, retorne `revenue_cents: null` ou omita.

---

## Tarefas (baixa complexidade)

### Tarefa 1 — Confirmar fonte de verdade para compras

**Objetivo:** escolher como identificar uma compra.

Escolha UMA:
- [ ] **Opção A:** compra = `price_impressions.attributed_purchase == true`
- [ ] **Opção B:** compra = documento em `purchases`

**Recomendação:** Opção A, porque liga direto à exposição/variante.

**Aceite:**
- [ ] Você sabe exatamente onde contar compras.

---

### Tarefa 2 — Criar Cloud Function `getSalesVariantSummary`

**Objetivo:** retornar contagens agregadas por `variant_id`.

**Input (request JSON):**
- `start_ms` (opcional)
- `end_ms` (opcional)

Se não vier, usar padrão: `end_ms = now`, `start_ms = now - 30 dias`.

**Implementação (Estrutura 1: price_impressions):**

1) Buscar `shows` por variante:
- query em `price_impressions` com filtro de data `shown_at` entre start/end
- agregar contagem por `variant_id`

2) Buscar `purchases` por variante:
- mesma query, porém filtrando também `attributed_purchase == true`
- agregar contagem por `variant_id`

3) Calcular `purchase_rate[variant] = purchases[variant] / shows[variant]` (com proteção para divisão por zero)

4) (Opcional) Receita:
- Se existir `purchase_value_cents` no impression, somar por variante.
- Se não existir, pular.

**Aceite:**
- [ ] Chamando a function, ela retorna JSON com `purchases` e `shows` por A/B/C.

> Observação: Firestore não tem agregação group-by nativa tão simples quanto SQL. Para baixa complexidade, faça:
> - ler os documentos do período e somar em memória (ok para poucos dados)
> - ou usar `count()` aggregation se você preferir, mas ainda precisará de 3 queries por variante.

---

### Tarefa 3 — (Alternativa mais eficiente) Criar coleção de agregados diários

**Objetivo:** evitar ler muitos documentos quando crescer.

Criar `daily_variant_stats/{yyyy-mm-dd}` com:
- `shows_A`, `shows_B`, `shows_C`
- `purchases_A`, `purchases_B`, `purchases_C`
- `revenue_A`, `revenue_B`, `revenue_C` (opcional)

**Como preencher (baixa complexidade):**
- No momento que você cria um impression: incrementa `shows_{variant}` do dia.
- No momento que atribui purchase: incrementa `purchases_{variant}` do dia.

Depois, `getSalesVariantSummary` só soma 30 docs (um por dia).

**Aceite:**
- [ ] O relatório fica rápido e barato.

---

### Tarefa 4 — Mostrar o relatório no app (opcional)

**Objetivo:** visualizar sem ferramentas externas.

- [ ] Criar uma tela `Admin/Debug` com um botão “Atualizar relatório”.
- [ ] Exibir:
  - compras por variante (A/B/C)
  - shows por variante
  - taxa de compra

**Aceite:**
- [ ] Você consegue ver rapidamente “quantas compras com preço maior/menor/normal”.

---

## Checklist final

- [ ] Existe uma function `getSalesVariantSummary`
- [ ] Ela retorna contagem de **compras por variante**
- [ ] (Opcional) retorna shows, taxa e receita

---

## Pergunta rápida (para ajustar a implementação)

Você está registrando a compra como:
1) `price_impressions.attributed_purchase == true` (e tem `shown_at`)
OU
2) coleção `purchases`?

Se você responder, eu posso ajustar este prompt para a sua estrutura exata.
