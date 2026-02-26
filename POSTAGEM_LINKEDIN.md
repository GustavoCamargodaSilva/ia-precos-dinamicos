A IA não está reduzindo preços. Está descobrindo o máximo que você aceita pagar.

Assisti um vídeo que me fez repensar tudo o que eu ouvia sobre "IA vai baratear as coisas".

A realidade?

Uber cobra mais quando chove.
Apps mostram preços diferentes para pessoas diferentes.
Sistemas analisam seu comportamento para calcular até onde você vai antes de desistir.

Isso tem nome: surveillance pricing — precificação por vigilância.

E não é teoria da conspiração. É engenharia.

Então eu pensei: se isso já acontece no mundo real, por que não construir um projeto que mostra exatamente COMO funciona por dentro?

Foi o que eu fiz.

Criei um app Android de loja simulada onde a IA decide os preços em tempo real.

Como funciona na prática:

O sistema testa 3 faixas de preço para cada produto — 10% abaixo, normal e 10% acima. A cada interação do usuário, ele aprende qual preço converte mais. Sem ninguém programar "use o preço X". O algoritmo descobre sozinho.

Mas vai além do preço do produto.

O app também analisa o comportamento no carrinho:
- Quantos itens você adicionou
- Se ficou indo e voltando
- Se removeu algum item (sinal de dúvida)
- Se já clicou em finalizar compra

Com base nisso, ele calcula uma "pontuação de propensão" — basicamente, qual a chance de você comprar. E decide:

- Chance alta? Sem desconto. Você já ia comprar.
- Chance média? Talvez um cupom de 5%.
- Chance baixa? Cupom de 10% pra não perder a venda.

Tudo automático. Tudo em tempo real.

O que tem por trás:

- App Android nativo (Kotlin + Jetpack Compose)
- Backend inteligente na nuvem (Firebase Cloud Functions)
- Algoritmo Thompson Sampling — a mesma família de IA usada por empresas de e-commerce
- Banco de dados que guarda cada tentativa e resultado (Firestore)
- Analytics rastreando 8 tipos de evento do usuário
- Relatório mostrando qual estratégia de preço está ganhando

A parte que mais me interessa:

Esse projeto não é sobre defender ou atacar precificação dinâmica. É sobre ENTENDER.

Quando você vê o código, os dados e as decisões acontecendo, você para de ser só consumidor passivo dessas tecnologias. Você entende a lógica.

E aí pode decidir com mais consciência — como profissional, como consumidor, como cidadão.

A pergunta que fica:

Se um projeto pessoal já consegue fazer isso com 10 produtos simulados, imagina o que sistemas com bilhões de dados fazem todos os dias com você?

--

#InteligenciaArtificial #PrecificacaoDinamica #Android #Kotlin #Firebase #ThompsonSampling #MachineLearning #Tecnologia #DataScience #Desenvolvimento
