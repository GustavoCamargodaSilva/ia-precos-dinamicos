# Prompt (.md) — Próximos Passos após Bandit Funcionando: Otimizar por Purchase + Exploração + Relatórios

**Contexto:** Você já implementou e validou `prompt_impl_bandit_firestore_functions.md`.
Ou seja, já existem:
- Cloud Function `getPriceBandit` (Thompson + bootstrap) que grava `price_impressions` e incrementa `shows`
- Cloud Function `recordOutcomeAddToCart` que incrementa `successes` por `context_key` e `variant_id`
- App Kotlin registra `price_shown` com `impression_id` e chama outcome no `add_to_cart`

**Objetivo desta etapa:** responder melhor perguntas do tipo:
- “Qual a chance de fechar o pedido quando já tem itens no carrinho?”
- “Se eu abaixar o preço, aumenta a chance de compra?”

Para isso, você vai:
1) adicionar **outcome de purchase** (atribuição correta)
2) manter **exploração mínima** (para não “parar de testar”)
3) criar **relatórios simples** por variante/contexto
4) (opcional) trocar recompensa para **composta** (add_to_cart + begin_checkout + purchase)

> Importante: com 2–3 usuários, a estatística é fraca; o foco é validar o pipeline e a lógica.

## 1) Decisões do desenho (escolhas)

Escolha e aplique estas convenções (simples):

### 1.1 Janela de atribuição
- Para `add_to_cart`: 30 minutos após `shown_at`.
- Para `purchase`: 24 horas após `shown_at`.

### 1.2 Purchase é “do carrinho inteiro”
Assumir que `purchase` representa a compra do carrinho inteiro em um clique.
A atribuição será feita para **todos os impressions dos itens presentes no carrinho**, ou para o impression mais recente (escolha 1).

**Escolha 1 (mais simples):** atribuir a compra ao **último impression_id usado para adicionar qualquer item ao carrinho** (um único credit).

**Escolha 2 (mais detalhada):** atribuir a compra para **cada item** do carrinho (múltiplos credits).

Neste prompt, use **Escolha 1**.

### 1.3 Exploração mínima
Manter exploração permanente:
- `epsilon = 0.10` (10% das decisões escolhem variante aleatória)
- 90% seguem Thompson

## 2) Alterações no Firestore

### 2.1 Expandir `bandit_stats`
Hoje você tem algo como:
- `shows`
- `successes` (provavelmente para add_to_cart)

Agora vamos separar métricas:
- `shows`
- `success_add_to_cart`
- `success_begin_checkout`
- `success_purchase`

Caminho:
- `bandit_stats/{context_key}/variants/{variant_id}`

Campos (numbers):
- `shows`
- `success_add_to_cart`
- `success_begin_checkout`
- `success_purchase`
- `updated_at`

### 2.2 Expandir `price_impressions`
Adicionar campos (se ainda não existem):
- `policy: string` ("thompson" | "explore")
- `attributed_add_to_cart: boolean`
- `attributed_begin_checkout: boolean`
- `attributed_purchase: boolean`

> Isso evita dupla contagem por outcome.

## 3) Backend — Cloud Functions (baixa complexidade)

### Tarefa 1 — Adicionar exploração (`epsilon`) no `getPriceBandit`

**Objetivo:** garantir que o sistema continue testando variantes.

**Checklist:**
- [ ] Definir `EPSILON = 0.10`.
- [ ] Sortear `u = Math.random()`.
- [ ] Se `u < EPSILON`:
  - escolher `variant_id` aleatoriamente entre A/B/C
  - setar `policy = "explore"`
- [ ] Caso contrário:
  - usar Thompson/bootstrapping como já existe
  - setar `policy = "thompson"`
- [ ] Gravar `policy` no documento `price_impressions/{impression_id}`.

**Aceite:**
- [ ] No Firestore, algumas impressions aparecem com `policy="explore"`.

### Tarefa 2 — Criar Function `recordOutcomeBeginCheckout`

**Objetivo:** capturar um sinal intermediário do funil.

**Request (JSON):**
- `installation_id`
- `impression_id`

**Lógica:**
1. Ler impression.
2. Validar `installation_id`.
3. Se `attributed_begin_checkout == true`, não fazer nada.
4. Se `expires_at` (30 min) já passou, decidir se ainda atribui (recomendado: **não atribuir**).
5. Marcar `attributed_begin_checkout = true`.
6. Incrementar `success_begin_checkout` em `bandit_stats/{context_key}/variants/{variant_id}`.

**Aceite:**
- [ ] `success_begin_checkout` incrementa quando usuário inicia checkout.

### Tarefa 3 — Criar Function `recordOutcomePurchase`

**Objetivo:** atribuir compra ao bandit.

**Request (JSON):**
- `installation_id`
- `impression_id`
- opcional: `value_cents` e `items_count` (para logging)

**Lógica:**
1. Ler impression.
2. Validar `installation_id`.
3. Se `attributed_purchase == true`, não fazer nada.
4. Validar janela de 24h:
   - usar `shown_at + 24h` como limite (pode criar `purchase_expires_at` ou calcular na hora)
5. Marcar `attributed_purchase = true`.
6. Incrementar `success_purchase` em `bandit_stats/{context_key}/variants/{variant_id}`.

**Aceite:**
- [ ] `success_purchase` cresce com compras simuladas.

### Tarefa 4 — Ajustar `recordOutcomeAddToCart` para escrever em `success_add_to_cart`

**Objetivo:** separar métricas corretamente.

**Checklist:**
- [ ] Renomear/incrementar campo `success_add_to_cart`.
- [ ] Usar `attributed_add_to_cart` no impression.

**Aceite:**
- [ ] Não há dupla contagem se usuário clicar add_to_cart duas vezes.

## 4) App Kotlin — tarefas

### Tarefa 1 — Propagar `impression_id` até o carrinho

**Checklist:**
- [ ] Garantir que cada `CartItem` armazene `impressionId` (do quote usado ao adicionar).
- [ ] No carrinho, guardar um campo `lastImpressionId` (o mais recente) para atribuição do purchase.

**Aceite:**
- [ ] Ao finalizar compra, você tem um `impression_id` para mandar ao backend.

### Tarefa 2 — Chamar `recordOutcomeBeginCheckout`

**Checklist:**
- [ ] Ao clicar em “Finalizar compra” (antes da confirmação), chamar `recordOutcomeBeginCheckout` com `lastImpressionId`.

**Aceite:**
- [ ] Firestore incrementa begin_checkout.

### Tarefa 3 — Chamar `recordOutcomePurchase` no purchase simulado

**Checklist:**
- [ ] Ao confirmar compra simulada:
  - chamar `recordOutcomePurchase(installation_id, lastImpressionId, value_cents, items_count)`
  - logar evento Analytics `purchase` com `impression_id`, `variant_id`, `context_key`

**Aceite:**
- [ ] `success_purchase` incrementa.

## 5) Relatórios simples (sem BigQuery)

### Tarefa 1 — Function `getBanditReport`

**Objetivo:** conseguir visualizar rapidamente performance por contexto.

**Request (JSON):**
- `context_key` (opcional)

**Response:**
- para cada variante: shows, success_add_to_cart, success_begin_checkout, success_purchase
- taxas calculadas (ex.: purchase_rate = success_purchase / shows)

**Aceite:**
- [ ] Você consegue chamar e ver um JSON com métricas.

## 6) Otimização (opcional): escolher qual métrica o bandit otimiza

Atualmente Thompson Sampling usa sucesso binário. Você tem 3 opções simples:

### Opção A (mais simples): otimizar apenas `purchase`
- sucesso = `success_purchase`

### Opção B: otimizar `begin_checkout` (se purchase é raro)
- sucesso = `success_begin_checkout`

### Opção C: otimizar “composta” com créditos
- tratar `purchase` como sucesso e os outros como “quase sucesso” via heurística:
  - ex.: a cada `begin_checkout`, incrementar também 0.3 em um campo separado (ex.: `reward_sum`)

> Se quiser manter Thompson Sampling puro (Beta-Bernoulli), escolha Opção A ou B.

## Checklist final

- [ ] `getPriceBandit` com `epsilon` + `policy` gravado
- [ ] outcomes para `add_to_cart`, `begin_checkout`, `purchase` sem dupla contagem
- [ ] atribuição por `impression_id` com janelas
- [ ] relatório JSON de performance por contexto/variante

## Observação sobre a pergunta “se abaixar o preço a pessoa compra?”

Com exploração ativa + atribuição correta + outcome de purchase, você terá evidência experimental (ainda ruidosa com poucos usuários) de como variantes impactam conversão por contexto. Isso é o caminho mais correto para inferir esse efeito em um sistema de preço dinâmico.
