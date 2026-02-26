const admin = require("firebase-admin");

admin.initializeApp({ projectId: "loja-simulada-precificacao" });
const db = admin.firestore();

const products = [
  { productId: "prod_001", name: "Fone Bluetooth", description: "Fone de ouvido sem fio com cancelamento de ruído", basePriceCents: 14990, imageUrl: null, category: "eletronicos" },
  { productId: "prod_002", name: "Camiseta Básica", description: "Camiseta 100% algodão, confortável para o dia a dia", basePriceCents: 4990, imageUrl: null, category: "roupas" },
  { productId: "prod_003", name: "Café Premium 500g", description: "Café torrado e moído, blend especial", basePriceCents: 3490, imageUrl: null, category: "alimentos" },
  { productId: "prod_004", name: "Carregador USB-C", description: "Carregador rápido 20W com cabo incluso", basePriceCents: 7990, imageUrl: null, category: "eletronicos" },
  { productId: "prod_005", name: "Mochila Casual", description: "Mochila resistente à água com compartimento para notebook", basePriceCents: 12990, imageUrl: null, category: "acessorios" },
  { productId: "prod_006", name: "Chocolate Artesanal 200g", description: "Chocolate 70% cacau, origem única", basePriceCents: 2490, imageUrl: null, category: "alimentos" },
  { productId: "prod_007", name: "Mouse Sem Fio", description: "Mouse ergonômico com sensor óptico de alta precisão", basePriceCents: 8990, imageUrl: null, category: "eletronicos" },
  { productId: "prod_008", name: "Calça Jeans Slim", description: "Calça jeans com elastano, corte slim fit", basePriceCents: 11990, imageUrl: null, category: "roupas" },
  { productId: "prod_009", name: "Garrafa Térmica 750ml", description: "Mantém a temperatura por até 12 horas", basePriceCents: 6990, imageUrl: null, category: "acessorios" },
  { productId: "prod_010", name: "Azeite Extra Virgem 500ml", description: "Azeite importado, acidez máxima 0.5%", basePriceCents: 4290, imageUrl: null, category: "alimentos" },
];

async function seed() {
  const batch = db.batch();
  for (const product of products) {
    const ref = db.collection("products").doc(product.productId);
    batch.set(ref, product);
  }
  await batch.commit();
  console.log(`${products.length} produtos inseridos no Firestore!`);
}

seed().catch(console.error);
