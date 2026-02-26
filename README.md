# Loja Simulada — Precificação Inteligente

## O que é este projeto?

Este é um aplicativo de loja virtual para celulares Android que ajusta preços e ofertas automaticamente para cada cliente, usando inteligência artificial. O objetivo é encontrar o melhor preço para cada situação — um preço justo que o cliente aceite e que traga o melhor resultado para a loja.

Pense assim: da mesma forma que um vendedor experiente sabe quando oferecer um desconto e quando manter o preço, este app aprende sozinho qual estratégia funciona melhor para cada tipo de cliente.

---

## Funcionalidades do App

### Para o cliente (telas do app)

| Funcionalidade | O que faz |
|---|---|
| **Catálogo de Produtos** | Mostra 10 produtos simulados (fones, roupas, café, etc.) com nome e preço |
| **Detalhes do Produto** | Ao tocar num produto, mostra descrição completa e o preço personalizado |
| **Carrinho de Compras** | Permite adicionar, remover e alterar quantidades. Salva mesmo se fechar o app |
| **Oferta no Carrinho** | Pode aparecer um desconto especial (5% ou 10%) quando o sistema detecta que vale a pena |
| **Finalização de Compra** | Tela de checkout simulado que confirma o pedido |
| **Configurações** | Permite escolher o estado (UF) e o tipo de aparelho (básico, médio ou topo de linha) |
| **Relatório de Vendas** | Mostra como cada variante de preço está performando (quantas vezes foi exibida, quantas vendas gerou) |

---

## Tecnologias Usadas (Stacks)

### O que cada tecnologia faz, em linguagem simples:

| Tecnologia | Para que serve |
|---|---|
| **Kotlin** | A linguagem de programação usada para construir o app Android |
| **Jetpack Compose** | Ferramenta do Google para criar as telas do app de forma moderna e visual |
| **Material Design 3** | O "estilo visual" do app — cores, botões, cards. Segue o padrão visual do Google |
| **Firebase Analytics** | Serviço do Google que registra tudo que o usuário faz no app (qual produto viu, o que adicionou ao carrinho, se comprou) |
| **Firebase Cloud Functions** | Um "cérebro na nuvem" que roda a inteligência artificial de preços. O app pergunta ao servidor qual preço mostrar |
| **Cloud Firestore** | Banco de dados na nuvem do Google que guarda as estatísticas de cada preço (quantas vezes foi mostrado, quantas vezes deu certo) |
| **DataStore** | Memória local do celular que guarda o carrinho e as preferências do usuário mesmo depois de fechar o app |
| **TypeScript / Node.js** | Linguagem usada no servidor (na nuvem) para rodar as funções de inteligência de preços |

---

## A Inteligência Artificial: Como os Preços São Decididos

### O Algoritmo — Thompson Sampling (Bandido Contextual)

Imagine que você tem 3 máquinas caça-níquel e quer descobrir qual dá mais prêmios. Você não pode jogar em todas ao mesmo tempo, então precisa decidir: **testar uma máquina nova ou repetir a que já deu bons resultados?**

Esse é exatamente o problema que o app resolve com preços:

- **Variante A** → Preço 10% mais barato que o base
- **Variante B** → Preço base (normal)
- **Variante C** → Preço 10% mais caro que o base

O algoritmo vai testando as 3 opções e, com o tempo, aprende qual funciona melhor para cada situação. Nas primeiras 10 vezes, ele testa todas igualmente. Depois, começa a favorecer a que dá mais resultado — mas nunca para totalmente de testar as outras, caso o comportamento mude.

### O que influencia a decisão de preço (Contexto)

O sistema não dá o mesmo preço para todo mundo. Ele considera:

| Fator | Exemplo | Por que importa |
|---|---|---|
| **Estado (UF)** | SP, RJ, MG... | Poder de compra e hábitos variam por região |
| **Tipo de aparelho** | Básico, Médio, Topo de linha | Indica o perfil econômico do usuário |
| **Dia do mês** | 5, 15, 28... | Perto do pagamento as pessoas tendem a gastar mais |
| **Tamanho do carrinho** | Vazio, 1-2 itens, 3+ itens | Quem já tem itens no carrinho tem mais intenção de compra |

### Ofertas Inteligentes no Carrinho

Além do preço do produto, o app também decide se vale a pena oferecer um cupom de desconto no carrinho:

- **Sem desconto (O0)** → Para quem já vai comprar de qualquer jeito
- **5% de desconto (O5)** → Para quem está em dúvida
- **10% de desconto (O10)** → Para quem provavelmente ia abandonar o carrinho

A decisão usa uma **pontuação de propensão** — uma nota de 0 a 1 que estima a chance do cliente comprar, baseada em:
- Quantos itens tem no carrinho
- Quanto tempo ficou olhando o carrinho
- Quantas vezes abriu o carrinho
- Se removeu itens (sinal de hesitação)

---

## Métricas: O Que o App Mede e Por Quê

Cada ação do usuário é registrada. Isso alimenta a inteligência artificial e gera relatórios.

| O que é medido | Por que é importante |
|---|---|
| **Produto visualizado** | Saber quais produtos atraem mais atenção |
| **Preço exibido** | Registrar exatamente qual preço (A, B ou C) foi mostrado para cada pessoa |
| **Adicionou ao carrinho** | Sinal de que o preço foi aceito — é o principal indicador de sucesso |
| **Removeu do carrinho** | Sinal de arrependimento ou hesitação |
| **Visualizou o carrinho** | Mostra interesse em finalizar a compra |
| **Iniciou checkout** | O cliente está quase comprando |
| **Compra realizada** | Sucesso total — o preço funcionou |
| **Oferta exibida** | Qual desconto foi mostrado e em que contexto |

Cada evento carrega informações como: qual variante de preço foi usada, em qual estado o cliente está, que tipo de aparelho usa, e um identificador único que liga a exibição do preço ao resultado final (comprou ou não).

---

## Serviços na Nuvem (Firebase / Google Cloud)

| Serviço | O que faz neste projeto |
|---|---|
| **Firebase Analytics** | Coleta todos os eventos do app (visualizações, cliques, compras) e envia para o Google |
| **Cloud Functions** | Servidor que roda 8 funções de inteligência: decidir preço, decidir oferta, registrar resultados, gerar relatórios |
| **Cloud Firestore** | Banco de dados que guarda quantas vezes cada preço foi testado e quantas vezes deu certo |

### As 8 funções do servidor:

| Função | O que faz |
|---|---|
| `getPriceBandit` | Decide qual preço (A, B ou C) mostrar para o cliente |
| `getCartOfferBandit` | Decide se oferece desconto no carrinho (0%, 5% ou 10%) |
| `recordOutcomeAddToCart` | Registra quando o cliente adiciona ao carrinho (preço "funcionou") |
| `recordOutcomeBeginCheckout` | Registra quando o cliente inicia o checkout |
| `recordOutcomePurchase` | Registra quando a compra é concluída |
| `recordOfferOutcomePurchase` | Registra resultado da oferta de desconto |
| `getSalesVariantSummary` | Gera relatório de desempenho das variantes de preço |
| `getOfferSummary` | Gera relatório de desempenho das ofertas de desconto |

---

## Resumo Visual

```
┌─────────────┐       ┌──────────────────┐       ┌─────────────┐
│  App Android │──────▶│  Cloud Functions  │──────▶│  Firestore  │
│  (Kotlin)    │◀──────│  (Inteligência)   │◀──────│  (Memória)  │
└─────────────┘       └──────────────────┘       └─────────────┘
       │                       │
       ▼                       ▼
┌─────────────┐       ┌──────────────────┐
│  DataStore   │       │ Firebase Analytics│
│  (local)     │       │ (métricas)        │
└─────────────┘       └──────────────────┘
```

1. O **app** mostra produtos e pede ao servidor qual preço usar
2. O **servidor** usa o algoritmo de IA para escolher a melhor variante
3. O **banco de dados** guarda o histórico de acertos e erros
4. O **analytics** registra cada ação do usuário para aprendizado contínuo
5. A **memória local** salva o carrinho e preferências no celular

---

## Em uma frase

> Um app de loja que usa inteligência artificial para testar diferentes preços e descontos automaticamente, aprendendo com cada interação qual estratégia maximiza as vendas para cada perfil de cliente.
