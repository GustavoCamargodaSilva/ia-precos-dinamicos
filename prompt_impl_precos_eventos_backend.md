# Prompt (.md) — Implementar Preço/Variantes + Instrumentação + Backend (Firebase Functions) — App Loja Simulada

**Contexto:** Você está trabalhando em um app Android (Kotlin) de loja simulada. O app já possui:
- catálogo mockado
- carrinho funcional (add/remove/quantidade)
- eventos Firebase Analytics básicos

**Objetivo desta etapa (sem IA ainda):** fechar o ciclo de medição para conseguir responder: **"qual preço o usuário viu e o que ele fez depois?"**

Você deve implementar:
1) evento `price_shown` (ou equivalentes) com metadados de variante e contexto
2) propagar `variant_id` para `add_to_cart` e `purchase`
3) backend mínimo (Cloud Function `getPrice`) para servir preço + variante e registrar/guardar consistência via Firestore
4) primeiro experimento A/B/n (3 variantes fixas) determinístico e auditável

## Regras e restrições

- Manter **baixa complexidade**: tarefas pequenas e sequenciais.
- Não introduzir IA ainda.
- Não coletar PII (email, telefone, nome real).
- O `installation_id` deve ser anônimo (ex.: UUID salvo em DataStore).
- Decisões de preço **devem vir do backend** (Cloud Function) para facilitar auditoria.

## Definições

### 1) Variantes de preço (A/B/n)

- `A`: desconto de 10% (multiplicador 0.90)
- `B`: preço base (multiplicador 1.00)
- `C`: aumento de 10% (multiplicador 1.10)

### 2) Contexto simulado

O app já tem (ou deve ter) uma tela/config que salva:
- `region_uf` (ex.: SP, RJ, MG)
- `device_tier` (low/mid/high)

Outros campos derivados:
- `day_of_month` (1 a 31)
- `device_model` (`Build.MODEL`)

Gerar uma string `context_key`:

Exemplo:
- `context_key = "SP|mid|day_12"`

## Saídas esperadas (o que deve estar pronto ao final)

1) O app sempre obtém um `price_quote` do backend ao abrir a tela de detalhe do produto (ou ao carregar produto).
2) O app registra um evento `price_shown` com:
   - `product_id`
   - `price_shown_cents`
   - `variant_id` (A/B/C)
   - `context_key`
3) Ao adicionar ao carrinho (`add_to_cart`) e ao finalizar compra (`purchase` simulado), os eventos incluem **o mesmo `variant_id`** que estava vigente para aquele produto.
4) Firestore tem uma coleção `price_decisions` com documentos que garantem consistência por 24h.
5) A alocação de variante é determinística por usuário (hash do `installation_id`).

## Plano de tarefas (baixa complexidade)

### Tarefa 1 — Criar/confirmar `installation_id` anônimo

**Objetivo:** ter um identificador estável para alocação de variante.

**Checklist:**
- [ ] Implementar `InstallationIdRepository`:
  - se não existir, gerar `UUID.randomUUID().toString()` e salvar no DataStore
  - expor `suspend fun getInstallationId(): String`

**Aceite:**
- [ ] Reiniciar app não muda o `installation_id`.

### Tarefa 2 — Definir `context_key` no app

**Objetivo:** padronizar contexto para logs e backend.

**Checklist:**
- [ ] Criar `UserContextProvider` que retorna:
  - `regionUf`
  - `deviceTier`
  - `dayOfMonth`
  - `deviceModel`
  - `contextKey` no formato `UF|tier|day_XX`

**Aceite:**
- [ ] `context_key` aparece em logs (Logcat) para conferência.

### Tarefa 3 — Criar modelo `PriceQuote`

**Objetivo:** padronizar a resposta de preço.

**Checklist:**
- [ ] Criar data class `PriceQuote`:
  - `productId: String`
  - `priceCents: Int`
  - `variantId: String` ("A"|"B"|"C")
  - `validUntilEpochMs: Long`
  - `contextKey: String`

**Aceite:**
- [ ] ViewModel da tela de detalhe consegue armazenar um `PriceQuote`.

### Tarefa 4 — Implementar evento `price_shown`

**Objetivo:** registrar o preço mostrado.

**Checklist:**
- [ ] No `AnalyticsLogger`, adicionar `logPriceShown(quote: PriceQuote, category: String?)`.
- [ ] Parâmetros do evento:
  - `product_id`
  - `price_shown_cents`
  - `variant_id`
  - `context_key`
  - opcional: `category`, `device_model`, `day_of_month`, `region_uf`, `device_tier`
- [ ] Disparar `price_shown` quando o preço estiver pronto e exibido na UI.

**Aceite:**
- [ ] Evento aparece no DebugView do Firebase.

### Tarefa 5 — Amarrar `variant_id` no carrinho

**Objetivo:** garantir que `add_to_cart` e `purchase` consigam ser atribuídos à variante.

**Checklist:**
- [ ] Ajustar `CartItem` para incluir:
  - `unitPriceCents`
  - `variantId`
  - `contextKey`
  - `addedAtEpochMs`
- [ ] Ao adicionar ao carrinho a partir da tela de detalhes, usar o `PriceQuote` atual.
- [ ] Atualizar `logAddToCart` para incluir `variant_id` e `context_key`.

**Aceite:**
- [ ] Carrinho persiste e mantém `variant_id` por item.

### Tarefa 6 — Implementar eventos `purchase` e `begin_checkout` com variante

**Objetivo:** registrar conversões com dimensões de experimento.

**Checklist:**
- [ ] `begin_checkout`:
  - `cart_total_cents`, `cart_size`, opcional `variants_summary` (ex.: "A:2,B:1")
- [ ] `purchase` simulado:
  - `value_cents`, `items_count`, opcional `variants_summary`
- [ ] Garantir que o `variants_summary` seja derivado dos itens do carrinho.

**Aceite:**
- [ ] Evento `purchase` aparece no DebugView.

### Tarefa 7 — Criar projeto Firebase + Firestore + Functions (backend)

**Objetivo:** preparar backend mínimo para servir preço.

**Checklist:**
- [ ] Inicializar Firebase Functions (Node.js) no projeto.
- [ ] Habilitar Firestore.
- [ ] Criar coleção `price_decisions`.

**Aceite:**
- [ ] Deploy de uma function de teste ("hello") funcionando.

### Tarefa 8 — Implementar Cloud Function `getPrice`

**Objetivo:** retornar preço + variante + validade.

**Contrato sugerido (request):**
- `product_id`
- `base_price_cents`
- `installation_id`
- `context_key`

**Contrato sugerido (response):**
- `price_cents`
- `variant_id`
- `valid_until`

**Lógica (simples):**
1. Gerar `variant_id` determinístico por `installation_id` (hash -> A/B/C).
2. Calcular multiplicador (A=0.9, B=1.0, C=1.1).
3. Calcular `price_cents` com arredondamento.
4. Salvar/atualizar no Firestore um documento.

**Chave do doc (sugestão):**
- `installation_id + "_" + product_id` (ou subcoleção por usuário)

**Campos:**
- `installation_id`, `product_id`, `variant_id`, `price_cents`, `context_key`, `valid_until`

**Aceite:**
- [ ] Requisições repetidas retornam o mesmo preço enquanto `valid_until` não expirar.

### Tarefa 9 — Conectar o app à Function `getPrice`

**Objetivo:** buscar preço do backend.

**Checklist:**
- [ ] Criar `PricingRepository` no app:
  - chama a Function `getPrice`
  - retorna `PriceQuote`
- [ ] Na tela de detalhes, ao carregar produto:
  - pegar `installation_id`
  - pegar `context_key`
  - chamar `getPrice`
  - exibir preço retornado
  - logar `price_shown`

**Aceite:**
- [ ] O preço exibido é o retornado pela Function.

## Observações

- Com 2–3 usuários, a análise será ruidosa, mas o objetivo é validar o **pipeline**.
- Use `add_to_cart` como métrica principal (mais frequente) e `purchase` como secundária.

## Próximo passo (depois disso)

- Criar um painel simples de análise (mesmo manual) para comparar conversão por `variant_id`.
- Só depois, implementar bandit/IA no backend.
