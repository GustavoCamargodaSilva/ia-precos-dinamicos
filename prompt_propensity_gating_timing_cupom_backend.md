# Prompt (.md) — IA de Propensão (Score) no Backend + Gating/Timing de Cupom + Otimização Equilibrada (Purchase vs Receita Líquida)

**Você já tem:**
- Bandit de oferta/cupom no carrinho/checkout (`getCartOfferBandit` + outcomes)
- Firestore com `cart_offer_impressions` e `offer_bandit_stats` (ou equivalente)

**Objetivo desta etapa:**
1) Criar um **propensity score** (probabilidade de compra) no **backend** (Cloud Functions)
2) Usar esse score para:
   - decidir **se** vale oferecer cupom (gating)
   - decidir **quando** oferecer (timing)
3) Trocar o bandit “puro conversão” por uma **recompensa balanceada**:
   - maximizar `purchase` *e* proteger/otimizar `receita líquida` (order_value - discount)

> Mantém simples, sem ML pesado: começamos com heurística + logs para depois evoluir.

## 0) Definições

### 0.1 Métricas
- `purchase`: evento final (1/0)
- `net_revenue_cents = order_value_cents - discount_cents`

### 0.2 Oferta (ações)
- `O0`: sem desconto
- `O5`: 5% off
- `O10`: 10% off

### 0.3 Contexto
Use seu `offer_context_key` atual, mas agora **adicione** uma dimensão do score:
- `prop_bucket` (bucket do score):
  - `p0`: [0.00, 0.25)
  - `p1`: [0.25, 0.50)
  - `p2`: [0.50, 0.75)
  - `p3`: [0.75, 1.00]

Novo contexto (sugestão):
- `offer_context_key = "UF|tier|day_XX|intent_bucket|cart_value_bucket|prop_bucket"`

> Alternativa: não colocar `prop_bucket` no contexto para evitar explosão de cardinalidade. Em MVP, dá para colocar porque seu tráfego é baixo e é estudo.

## 1) Firestore — ajustes de schema

### 1.1 `cart_offer_impressions/{offer_impression_id}`
Adicionar campos:
- `propensity_score: number` (0–1)
- `prop_bucket: string` (p0..p3)
- `gate_decision: string` ("no_offer" | "bandit" | "force_offer")
- `eligible_offers: array<string>` (ex.: ["O0","O5"]) — quais ações estavam permitidas
- `timing_decision: string` ("on_view_cart" | "delayed" | "on_begin_checkout")

Adicionar (se não existir):
- `discount_cents: number`
- `order_value_cents: number` (no momento do purchase outcome, se possível)
- `net_revenue_cents: number` (computado no outcome)

### 1.2 `offer_bandit_stats/{offer_context_key}/variants/{offer_variant_id}`
Adicionar:
- `shows: number`
- `purchase_count: number`
- `net_revenue_sum_cents: number`
- `net_revenue_sq_sum: number` (opcional, para variância; pode pular)

> Se você já tem `success_purchase`, renomeie mentalmente para `purchase_count` (ou mantenha e some).

## 2) Backend — Function de score (propensão)

### Function A — `computePropensityScore`

**Responsabilidade:** dado o estado atual do usuário/carrinho, retornar `propensity_score` e `prop_bucket`.

**Request (JSON):**
- `installation_id`
- `cart_items_count`
- `cart_subtotal_cents`
- `num_cart_opens`
- `time_in_cart_sec`
- `removed_items_count`
- `begin_checkout_clicked` (0/1)
- `context_base` (ex.: UF/tier/day)

**Response (JSON):**
- `propensity_score`
- `prop_bucket`

**Implementação v1 (heurística, simples):**
Crie uma função que soma pontos e passa por uma sigmoide, ou use regras por faixas. Exemplo de regra (bem simples):
- Se `begin_checkout_clicked==1` → `p=0.85`
- Senão se `cart_items_count>=2` → `p=0.60`
- Senão se `cart_items_count==1` → `p=0.45`
- Senão → `p=0.15`

Ajustes finos:
- +0.05 se `num_cart_opens>=2`
- -0.05 se `removed_items_count>=1`
- clamp 0.01..0.99

**Aceite:**
- [ ] Score é estável (0–1) e muda quando o carrinho muda.

## 3) Backend — Gating (quem recebe cupom) e Timing (quando mostrar)

### 3.1 Função principal: `getCartOfferBanditV2`

**Objetivo:** unificar score + gating + bandit.

**Request (JSON):**
- `installation_id`
- `cart_subtotal_cents`
- `cart_items_count`
- `num_cart_opens`
- `time_in_cart_sec`
- `removed_items_count`
- `begin_checkout_clicked`
- `UF`, `device_tier`, `day_XX`

**Passos:**
1) Chamar internamente `computePropensityScore` (ou embutir a lógica)
2) Definir `gate_decision` + `eligible_offers`
3) Definir `timing_decision`
4) Se `gate_decision == "no_offer"` → retornar `O0` com `discount_cents=0`
5) Caso contrário → rodar bandit somente entre `eligible_offers`
6) Gravar impression com score, gating e timing
7) Incrementar `shows` da variante escolhida

### 3.2 Regras de gating (MVP)

- Se `propensity_score >= 0.75`:
  - `gate_decision = "no_offer"`
  - `eligible_offers = ["O0"]`

- Se `0.40 <= propensity_score < 0.75`:
  - `gate_decision = "bandit"`
  - `eligible_offers = ["O0","O5"]`

- Se `propensity_score < 0.40`:
  - `gate_decision = "force_offer"` (na prática: sempre oferecer algo)
  - `eligible_offers = ["O5","O10"]`

> Por que isso ajuda receita líquida? Usuários com alta intenção não ganham desconto.

### 3.3 Regras de timing (MVP)

- Se `propensity_score >= 0.75` → `timing_decision = "on_view_cart"` (mas vai dar O0)
- Se `0.40 <= p < 0.75` → `timing_decision = "on_view_cart"`
- Se `p < 0.40`:
  - se `time_in_cart_sec < 15` e `num_cart_opens == 1` → `timing_decision = "delayed"` (app espera 10–20s para mostrar)
  - senão → `timing_decision = "on_view_cart"`

**Aceite:**
- [ ] Cupom não aparece para quem já está muito provável de comprar.
- [ ] Para baixa propensão, cupom pode aparecer com delay.

## 4) Bandit — recompensa equilibrada (purchase vs receita líquida)

Você tem duas opções simples. Escolha UMA.

### Opção A (mais simples e robusta): 2 métricas + regra de seleção
- Bandit aprende **taxa de purchase** por oferta.
- Na decisão, você aplica uma penalidade por desconto para equilibrar receita.

Score de decisão por oferta:
- `decision_score = sampled_purchase_rate - lambda * (expected_discount_rate)`

Onde:
- `expected_discount_rate` é 0 para O0, 0.05 para O5, 0.10 para O10
- `lambda` (ex.: 0.3) controla o quanto você evita descontos.

Prós: mantém Beta-Bernoulli simples.

### Opção B (mais correta): otimizar `net_revenue_per_show`
- Para cada impression atribuída a purchase, compute `net_revenue_cents`.
- Mantenha estatística por variante:
  - `shows`
  - `net_revenue_sum_cents`
- Decida pela variante com maior `net_revenue_sum_cents/shows` (com exploração).

Prós: otimiza dinheiro.
Contras: com poucos dados, fica ruidoso.

**Recomendação:** começar com **Opção A**.

## 5) Outcomes — atualizar purchase + receita líquida

### Function B — `recordOfferOutcomePurchaseV2`

**Request:**
- `installation_id`
- `offer_impression_id`
- `order_value_cents`

**Lógica:**
1) Ler impression
2) Validar idempotência
3) Marcar `attributed_purchase=true`
4) Calcular:
   - `discount_cents` (já no impression)
   - `net_revenue_cents = max(order_value_cents - discount_cents, 0)`
5) Escrever no impression `order_value_cents`, `net_revenue_cents`
6) Incrementar em `offer_bandit_stats/.../variants/...`:
   - `purchase_count += 1`
   - `net_revenue_sum_cents += net_revenue_cents`

**Aceite:**
- [ ] Stats passam a ter purchase_count e net_revenue_sum.

## 6) App Kotlin — mudanças mínimas

### 6.1 Enviar features para o backend
No momento de abrir carrinho / atualizar carrinho:
- `cart_items_count`, `cart_subtotal_cents`
- `num_cart_opens` (contado localmente)
- `time_in_cart_sec` (cronômetro simples na tela)
- `removed_items_count` (se tiver)
- `begin_checkout_clicked` (0 no carrinho; 1 no evento de begin_checkout)

### 6.2 Aplicar timing `delayed`
Se response vier com `timing_decision == "delayed"`:
- esperar 10–20s
- então renderizar a oferta (ou chamar novamente para obter uma oferta atual)

**Aceite:**
- [ ] Oferta às vezes aparece após alguns segundos.

## 7) Relatório (para validar que ficou equilibrado)

Criar/estender `getOfferSummary` para mostrar por oferta:
- `shows`
- `purchase_count`
- `purchase_rate = purchase_count/shows`
- `net_revenue_sum_cents`
- `net_rev_per_show = net_revenue_sum_cents/shows`

E por bucket de propensão (p0..p3):
- mesmas métricas

**Aceite:**
- [ ] Você consegue ver que p3 recebe quase sempre O0.
- [ ] Você consegue ver trade-off: O10 pode aumentar purchase mas reduzir net_rev_per_show.

## Checklist final

- [ ] `computePropensityScore` no backend
- [ ] `getCartOfferBanditV2` aplica gating + timing + bandit
- [ ] outcomes gravam `order_value_cents` e `net_revenue_cents`
- [ ] relatórios mostram conversão e receita líquida por oferta e por propensão
