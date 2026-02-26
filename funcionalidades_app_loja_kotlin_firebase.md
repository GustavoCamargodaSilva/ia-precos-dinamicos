# Documento de Funcionalidades e Tarefas (Baixa Complexidade) — App Loja Simulada (Kotlin + Firebase)

**Objetivo:** criar um app Android básico em Kotlin que simula uma loja de produtos, com catálogo **mockado** (local ou seed na base) e **carrinho funcional** (adicionar/remover/alterar quantidade), além de **coleta de eventos** para uso futuro com IA e precificação dinâmica via backend.

Este documento foi escrito para ser executado em pequenas tarefas por um assistente (ex.: Claude), com foco em **baixa complexidade** e progresso incremental.

## 1. Escopo do MVP

### 1.1 O que o app deve fazer (MVP)

- Exibir uma lista de produtos (mock local ou inseridos na base Firestore no primeiro run).
- Exibir detalhes do produto (nome, descrição curta, imagem placeholder, preço base).
- Carrinho funcional:
  - adicionar item
  - remover item
  - alterar quantidade
  - limpar carrinho
- Persistência simples do carrinho (local) para não perder ao fechar o app.
- Registrar eventos principais (Analytics):
  - `app_open`
  - `view_product`
  - `add_to_cart`
  - `remove_from_cart`
  - `view_cart`
  - `begin_checkout` (simulado)
  - `purchase` (simulado)
- Tela de “Configuração de contexto” (simulada):
  - região (UF)
  - `device_tier` (low/mid/high)
  - selecionado pelo usuário e salvo localmente

### 1.2 Fora de escopo (por enquanto)

- Login/autenticação real (usar usuário anônimo / installation id se necessário).
- Pagamentos reais (checkout e `purchase` serão simulados).
- Precificação dinâmica real via Cloud Functions (planejado para fase 2).
- Recomendação personalizada / IA embarcada (planejado para fase 3).

## 2. Stack sugerida

- Kotlin + Android Studio.
- UI: **Jetpack Compose** (preferido) ou XML (aceitável).
- Arquitetura: MVVM simples.
- Persistência local do carrinho: **DataStore** (preferido) ou Room (se quiser mais estrutura).
- Firebase: **Analytics** (coleta de eventos).
- Firebase: **Firestore** opcional (para mock de produtos remoto ou seed).

> Nota: Para manter baixa complexidade, o catálogo pode começar como JSON local em `assets/`. Firestore entra apenas se você quiser treinar o fluxo de dados desde o início.

## 3. Modelos de dados (simples)

### 3.1 Produto

- `productId: String`
- `name: String`
- `description: String`
- `basePriceCents: Int`
- `imageUrl: String?` (placeholder)
- `category: String`

### 3.2 Item do carrinho

- `productId: String`
- `quantity: Int`
- `unitPriceCents: Int` (preço exibido no momento de adicionar — útil para auditoria futura)
- `addedAtEpochMs: Long`

### 3.3 Contexto simulado do usuário

- `regionUF: String` (ex.: SP, RJ, MG)
- `deviceTier: String` (low/mid/high)
- `dayOfMonth: Int` (derivado em runtime)
- `deviceModel: String` (`Build.MODEL`)

## 4. Telas e Fluxos

### 4.1 Tela: Catálogo

- Lista de produtos com nome + preço base.
- Ao clicar, navega para Detalhes.
- Registrar evento: `view_catalog` (custom) ou `view_item_list` (padrão GA4).

### 4.2 Tela: Detalhes do Produto

- Mostrar informações do produto.
- Botão “Adicionar ao carrinho”.
- Registrar evento: `view_product` com `product_id` e `price_shown_cents`.

### 4.3 Tela: Carrinho

- Listar itens com quantidade e subtotal.
- Ações:
  - `+1`
  - `-1`
  - remover item
  - limpar carrinho
- Mostrar total.
- Registrar eventos: `view_cart`, `add_to_cart`, `remove_from_cart`.

### 4.4 Tela: Contexto (Configurações)

- Dropdown para UF (região simulada).
- Dropdown para `device_tier` (simulado).
- Salvar no DataStore.
- Registrar evento: `update_context` (opcional).

### 4.5 Checkout / Purchase (Simulado)

- Botão “Finalizar compra” no carrinho.
- Tela simples de confirmação.
- Ao confirmar:
  - registrar `begin_checkout` e `purchase` (simulado)
  - limpar carrinho

## 5. Coleta de eventos (Firebase Analytics)

Todos os eventos devem incluir um conjunto mínimo de parâmetros para permitir análises futuras, evitando dados sensíveis.

### 5.1 Eventos mínimos e parâmetros

- `app_open` (automático + opcional custom).
- `view_product`:
  - `product_id`
  - `category`
  - `price_shown_cents`
  - `region_uf`
  - `device_tier`
  - `device_model`
  - `day_of_month`
- `add_to_cart`:
  - `product_id`
  - `quantity`
  - `unit_price_cents`
  - `cart_size`
- `remove_from_cart`:
  - `product_id`
  - `quantity`
  - `cart_size`
- `view_cart`:
  - `cart_size`
  - `cart_total_cents`
- `begin_checkout`:
  - `cart_size`
  - `cart_total_cents`
- `purchase` (simulado):
  - `value_cents`
  - `items_count`

### 5.2 Regras simples

- Usar nomes **snake_case** para eventos e parâmetros.
- Evitar enviar PII (nome real, email, telefone).
- Se precisar identificar usuário, usar `installation_id` anônimo e estável.

## 6. Tarefas (baixa complexidade) — para Claude executar

> Cada tarefa deve resultar em app compilando e rodando.

### Tarefa 1 — Setup do projeto

1. Criar projeto Android em Kotlin.
2. Escolher Jetpack Compose (recomendado) e configurar Navigation Compose.
3. Adicionar dependências básicas (Compose, lifecycle-viewmodel, coroutines).

### Tarefa 2 — Integração Firebase Analytics

1. Criar projeto no Firebase Console e baixar `google-services.json`.
2. Adicionar plugin Google Services no Gradle.
3. Inicializar Firebase Analytics e criar um wrapper `AnalyticsLogger` com funções:
   - `logViewProduct(...)`
   - `logAddToCart(...)`
   - `logRemoveFromCart(...)`
   - `logViewCart(...)`
   - `logBeginCheckout(...)`
   - `logPurchaseSimulated(...)`

### Tarefa 3 — Catálogo mockado

1. Criar lista mockada de 10 produtos (em código ou JSON em `assets/`).
2. Criar `Repository` simples para fornecer produtos.
3. Tela de Catálogo exibindo lista e navegando para Detalhes.

### Tarefa 4 — Tela de Detalhes + evento `view_product`

1. Criar tela de detalhe do produto.
2. Ao abrir, disparar log do evento `view_product` com parâmetros mínimos.
3. Adicionar botão “Adicionar ao carrinho”.

### Tarefa 5 — Carrinho funcional (local) + eventos add/remove

1. Implementar `CartManager` (ViewModel) com estado em memória + persistência via DataStore.
2. Permitir adicionar/remover e alterar quantidade.
3. Criar tela do Carrinho com total e ações.
4. Logar `add_to_cart`, `remove_from_cart`, `view_cart`.

### Tarefa 6 — Contexto simulado (UF + device tier)

1. Criar DataStore para salvar `regionUF` e `deviceTier`.
2. Criar tela simples de configurações com dropdowns.
3. Incluir esses valores como parâmetros em todos os eventos.

### Tarefa 7 — Checkout simulado

1. Adicionar botão “Finalizar compra”.
2. Tela de confirmação.
3. Ao confirmar:
   - logar `begin_checkout` e `purchase` (simulado)
   - limpar carrinho

### Tarefa 8 — (Opcional) Seed no Firestore

1. Criar coleção `products` no Firestore.
2. Implementar um botão “Seed products” (somente debug) para inserir os produtos mockados.
3. Alternar `Repository` para ler do Firestore (com fallback local se vazio).

## 7. Critérios de aceite (MVP)

- App compila e roda em emulador Android.
- Catálogo aparece com produtos mockados.
- Carrinho funciona e persiste ao reiniciar o app.
- Eventos são disparados (verificáveis via **DebugView** do Firebase).
- Checkout simulado limpa carrinho e dispara `purchase`.

## 8. Próximos passos (fase 2: preço dinâmico + IA)

- Criar Cloud Function `getPrice(productId, context)` e retornar `variant_id` e `price_cents`.
- Registrar `price_shown` e associar com `add_to_cart`/`purchase`.
- Implementar bandit simples (epsilon-greedy ou Thompson Sampling) no backend, com estatísticas no Firestore.
