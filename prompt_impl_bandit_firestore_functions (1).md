# Prompt (.md) — Implementar “IA” de Decisão (Contextual Bandit) para Preço Dinâmico — Firebase Functions + Firestore

**Objetivo:** substituir a regra fixa A/B/C por um algoritmo que **aprende** (bandit simples) qual variante de preço maximiza conversão, usando **estatísticas no Firestore** e decisão no **backend**.

Este prompt assume que você já tem:
- App Kotlin chamando uma Function `getPrice` (ou equivalente) que retorna `price_cents`, `variant_id`, `valid_until`
- Eventos instrumentados: `price_shown`, `add_to_cart`, `begin_checkout`, `purchase` (simulado)
- Todos os eventos carregam `context_key` e `variant_id` (ou você consegue associar)

> Recomendação para pouco tráfego: use `add_to_cart` como sucesso inicial (mais frequente). Depois troque/adicione `purchase`.

## 0) Definições

### Variantes (ações)
- `A`: -10% (multiplicador 0.90)
- `B`: base (multiplicador 1.00)
- `C`: +10% (multiplicador 1.10)

### Contexto
Você já usa algo como:
- `context_key = "UF|tier|day_XX"`

**Nesta etapa, estenda para incluir estado do funil (carrinho):**
- `cart_bucket`:
  - `cart0` = carrinho vazio
  - `cart1_2` = 1–2 itens
  - `cart3p` = 3+ itens

Novo formato:
- `context_key = "UF|tier|day_XX|cart_bucket"`

Exemplo:
- `SP|mid|day_12|cart1_2`

### Ponto de decisão
Escolha **um** (para manter simples):
1. **Produto (recomendado)**: decidir preço ao exibir detalhes do produto (`price_shown`).
2. Carrinho: decidir um desconto/oferta ao abrir o carrinho.

Neste prompt, vamos assumir **Produto**.

### Recompensa (sucesso)
Defina sucesso como um evento observado após `price_shown`:
- **Sucesso v1:** `add_to_cart` do mesmo `product_id` dentro de uma janela (ex.: 30 min)
- **Sucesso v2:** `purchase` (simulado) dentro de uma janela maior (ex.: 24 h)

Comece com v1.

## 1) Estrutura de dados no Firestore

### 1.1 Coleção `bandit_stats`

Caminho sugerido:
- `bandit_stats/{context_key}/variants/{variant_id}`

Documento `variants/{variant_id}` contém:
- `shows: number` (quantas vezes essa variante foi mostrada nesse contexto)
- `successes: number` (quantas vezes teve sucesso nesse contexto)
- `updated_at: timestamp`

**Inicialização:** se não existir, tratar como `shows=0`, `successes=0`.

### 1.2 Coleção `price_impressions` (para atribuição)

Para conseguir atribuir sucesso corretamente, registre cada exibição com um id:
- `price_impressions/{impression_id}`

Campos:
- `impression_id: string`
- `installation_id: string`
- `product_id: string`
- `context_key: string`
- `variant_id: string`
- `price_cents: number`
- `shown_at: timestamp`
- `expires_at: timestamp` (ex.: shown_at + 30 min)
- `attributed: boolean` (default false)

> Observação: isso é deliberadamente simples. Em produção, você teria uma modelagem mais robusta por sessão.

## 2) IA no backend: Thompson Sampling (recomendado) ou Epsilon-Greedy

### 2.1 Thompson Sampling (Beta-Bernoulli)
Para cada variante, modelar taxa de sucesso como Beta:
- prior `Beta(1,1)`
- posterior `Beta(1+successes, 1+shows-successes)`

Decisão:
- amostrar uma taxa para A, B, C e escolher a maior.

> Por que isso funciona bem? Explora naturalmente e converge sem muita parametrização.

### 2.2 Guardrails (importante)
- **Min shows por contexto**: enquanto `total_shows < 10`, force exploração balanceada (round-robin) para coletar dados.
- **Limites de preço**: garantir que `price_cents` nunca seja < 1.

## 3) Cloud Functions a implementar

### Function A — `getPriceBandit`

**Responsabilidade:** escolher variante via bandit, calcular preço, registrar impression e incrementar `shows`.

**Request (JSON):**
- `installation_id`
- `product_id`
- `base_price_cents`
- `context_key` (já incluindo `cart_bucket`)

**Response (JSON):**
- `impression_id`
- `variant_id`
- `price_cents`
- `valid_until` (epoch ms)

**Checklist:**
- [ ] Ler stats de `bandit_stats/{context_key}/variants/{A|B|C}`.
- [ ] Calcular `total_shows`.
- [ ] Se `total_shows < MIN_BOOTSTRAP` (ex.: 9): escolher variante com menos `shows` (balanceado).
- [ ] Caso contrário: Thompson Sampling.
- [ ] Calcular `price_cents` = round(`base_price_cents` * multiplicador).
- [ ] Gerar `impression_id` (UUID).
- [ ] Gravar `price_impressions/{impression_id}`.
- [ ] Incrementar atomicamente `shows` da variante escolhida.

**Aceite:**
- [ ] Chamadas repetidas retornam respostas válidas.
- [ ] `bandit_stats` começa a acumular `shows`.

### Function B — `recordOutcomeAddToCart`

**Responsabilidade:** atribuir sucesso ao bandit quando ocorrer `add_to_cart`.

**Request (JSON):**
- `installation_id`
- `product_id`
- `impression_id` (ideal) **OU** (fallback) `context_key` + `variant_id`

**Lógica recomendada (com impression_id):**
1. Ler `price_impressions/{impression_id}`.
2. Validar:
   - `installation_id` e `product_id` batem
   - `expires_at` não expirou
   - `attributed == false`
3. Marcar `attributed = true`.
4. Incrementar `successes` em `bandit_stats/{context_key}/variants/{variant_id}`.

**Aceite:**
- [ ] `successes` cresce quando usuário adiciona ao carrinho.

> Observação: para simplificar, você pode disparar essa function diretamente do app quando o usuário faz add_to_cart.

## 4) Alterações no app (Kotlin)

### Tarefa 1 — Calcular `cart_bucket`

**Checklist:**
- [ ] No `CartManager`, expor `cartSize`.
- [ ] Criar função:
  - `cart_bucket = cart0` se size==0
  - `cart_bucket = cart1_2` se size in 1..2
  - `cart_bucket = cart3p` se size>=3
- [ ] Incluir `cart_bucket` na composição do `context_key`.

**Aceite:**
- [ ] `context_key` muda conforme carrinho muda.

### Tarefa 2 — Trocar chamada de preço para `getPriceBandit`

**Checklist:**
- [ ] `PricingRepository` chama a function `getPriceBandit`.
- [ ] Recebe `impression_id`, `variant_id`, `price_cents`, `valid_until`.
- [ ] Salva `impression_id` no `PriceQuote`.

**Aceite:**
- [ ] Cada `price_shown` tem um `impression_id`.

### Tarefa 3 — Logar `price_shown` com `impression_id`

**Checklist:**
- [ ] Atualizar evento `price_shown` para incluir:
  - `impression_id`
  - `variant_id`
  - `context_key`
  - `price_shown_cents`

**Aceite:**
- [ ] DebugView mostra `impression_id` no evento.

### Tarefa 4 — Ao `add_to_cart`, chamar `recordOutcomeAddToCart`

**Checklist:**
- [ ] No fluxo de adicionar ao carrinho (quando usuário clica “Adicionar”):
  - chamar function `recordOutcomeAddToCart` com `impression_id` (do `PriceQuote` vigente).
- [ ] Em paralelo, logar evento `add_to_cart` com `impression_id`, `variant_id`, `context_key`.

**Aceite:**
- [ ] `successes` incrementa no Firestore ao adicionar.

## 5) Observabilidade / Debug

- Criar um endpoint auxiliar (ou script) para ler `bandit_stats` e imprimir:
  - shows/successes por `context_key` e variante
- Conferir se, com o tempo, o bandit passa a escolher mais a variante com maior taxa de sucesso.

## 6) Extensões (depois que isso estiver ok)

1) Mudar sucesso para `purchase` (ou misto):
- atribuir sucesso quando `purchase` ocorrer (janela maior)

2) Recompensa com valor (se quiser):
- em vez de sucesso binário, usar “revenue” (mais complexo; requer bandit para recompensas reais)

3) Incluir feature “já tem item no carrinho?”
- já incluído via `cart_bucket` no `context_key`

---

## Checklist final (pronto para testar)

- [ ] `getPriceBandit` decide variante e grava impression
- [ ] `price_shown` inclui `impression_id`
- [ ] `add_to_cart` chama `recordOutcomeAddToCart` e incrementa successes
- [ ] `bandit_stats` acumula shows/successes por contexto
- [ ] Em alguns testes, distribuição de variantes começa a favorecer a melhor
