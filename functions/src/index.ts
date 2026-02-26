import {setGlobalOptions} from "firebase-functions";
import {onCall, onRequest} from "firebase-functions/v2/https";
import {getFirestore} from "firebase-admin/firestore";
import {initializeApp} from "firebase-admin/app";

initializeApp();
const db = getFirestore();

setGlobalOptions({maxInstances: 10});

// Seed: chamar uma vez, depois pode remover
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const makeProduct = (
  id: string, name: string, desc: string,
  price: number, cat: string,
// eslint-disable-next-line @typescript-eslint/no-explicit-any
): Record<string, any> => ({
  productId: id, name, description: desc,
  basePriceCents: price, imageUrl: null, category: cat,
});

export const seedProducts = onRequest(async (req, res) => {
  const products = [
    makeProduct("prod_001", "Fone Bluetooth",
      "Fone sem fio com cancelamento de ruido",
      14990, "eletronicos"),
    makeProduct("prod_002", "Camiseta Basica",
      "Camiseta 100% algodao", 4990, "roupas"),
    makeProduct("prod_003", "Cafe Premium 500g",
      "Cafe torrado e moido, blend especial",
      3490, "alimentos"),
    makeProduct("prod_004", "Carregador USB-C",
      "Carregador rapido 20W com cabo",
      7990, "eletronicos"),
    makeProduct("prod_005", "Mochila Casual",
      "Mochila resistente com compartimento notebook",
      12990, "acessorios"),
    makeProduct("prod_006", "Chocolate Artesanal 200g",
      "Chocolate 70% cacau", 2490, "alimentos"),
    makeProduct("prod_007", "Mouse Sem Fio",
      "Mouse ergonomico sensor optico",
      8990, "eletronicos"),
    makeProduct("prod_008", "Calca Jeans Slim",
      "Calca jeans com elastano slim fit",
      11990, "roupas"),
    makeProduct("prod_009", "Garrafa Termica 750ml",
      "Mantem temperatura por 12 horas",
      6990, "acessorios"),
    makeProduct("prod_010", "Azeite Extra Virgem 500ml",
      "Azeite importado acidez 0.5%",
      4290, "alimentos"),
  ];

  const batch = db.batch();
  for (const p of products) {
    batch.set(
      db.collection("products").doc(p.productId), p,
    );
  }
  await batch.commit();
  res.json({success: true, count: products.length});
});

// ============================================================
// Constantes compartilhadas
// ============================================================

const VARIANTS = ["A", "B", "C"] as const;
const MULTIPLIERS: Record<string, number> = {
  "A": 0.90,
  "B": 1.00,
  "C": 1.10,
};

const MIN_BOOTSTRAP = 9; // 3 por variante antes de usar Thompson
// 30 min para add_to_cart/begin_checkout
const IMPRESSION_TTL_MS = 30 * 60 * 1000;
const PURCHASE_TTL_MS = 24 * 60 * 60 * 1000; // 24h para purchase
const EPSILON = 0.10; // 10% exploracao aleatoria

// Offer Bandit Constants
const OFFER_VARIANTS = ["O0", "O5", "O10"] as const;
const OFFER_DISCOUNTS: Record<string, number> = {
  "O0": 0.00,
  "O5": 0.05,
  "O10": 0.10,
};
const OFFER_IMPRESSION_TTL_MS = 30 * 60 * 1000;
const OFFER_PURCHASE_TTL_MS = 24 * 60 * 60 * 1000;
const OFFER_MIN_BOOTSTRAP = 9;
const OFFER_EPSILON = 0.10;
const OFFER_LAMBDA = 0.30; // penalidade para equilibrar receita

// ============================================================
// Helpers
// ============================================================

/**
 * Amostra de uma distribuicao Beta usando Joehnk.
 * @param {number} alpha - Parametro alpha.
 * @param {number} beta - Parametro beta.
 * @return {number} Amostra entre 0 e 1.
 */
function sampleBeta(alpha: number, beta: number): number {
  // Joehnk's algorithm para Beta(a,b) quando a,b >= 1
  // Fallback simples: usar media + ruido para a,b pequenos
  if (alpha <= 0) alpha = 1;
  if (beta <= 0) beta = 1;

  // Metodo por transformacao de Gamma
  const ga = sampleGamma(alpha);
  const gb = sampleGamma(beta);
  return ga / (ga + gb);
}

/**
 * Amostra de Gamma(shape,1) via Marsaglia & Tsang.
 * @param {number} shape - Parametro shape (>0).
 * @return {number} Amostra positiva.
 */
function sampleGamma(shape: number): number {
  if (shape < 1) {
    return sampleGamma(shape + 1) * Math.pow(Math.random(), 1 / shape);
  }
  const d = shape - 1 / 3;
  const c = 1 / Math.sqrt(9 * d);
  // eslint-disable-next-line no-constant-condition
  while (true) {
    let x: number;
    let v: number;
    do {
      x = randn();
      v = 1 + c * x;
    } while (v <= 0);
    v = v * v * v;
    const u = Math.random();
    if (u < 1 - 0.0331 * (x * x) * (x * x)) return d * v;
    if (Math.log(u) < 0.5 * x * x + d * (1 - v + Math.log(v))) {
      return d * v;
    }
  }
}

/**
 * Gera amostra normal padrao via Box-Muller.
 * @return {number} Amostra N(0,1).
 */
function randn(): number {
  const u1 = Math.random();
  const u2 = Math.random();
  return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
}

/**
 * Gera UUID v4 simples.
 * @return {string} UUID.
 */
function generateUUID(): string {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(
    /[xy]/g,
    (c) => {
      const r = (Math.random() * 16) | 0;
      const v = c === "x" ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    }
  );
}

// ============================================================
// getPriceBandit — Thompson Sampling
// ============================================================

interface BanditRequest {
  installationId: string;
  productId: string;
  basePriceCents: number;
  contextKey: string;
}

interface VariantStats {
  shows: number;
  successes: number; // legacy, mantido para compatibilidade
  success_add_to_cart: number;
  success_begin_checkout: number;
  success_purchase: number;
}

/**
 * Le stats de bandit_stats/{contextKey}/variants/{A|B|C}.
 * @param {string} contextKey - Contexto do bandit.
 * @return {Promise<Record<string, VariantStats>>} Stats por variante.
 */
async function readBanditStats(
  contextKey: string
): Promise<Record<string, VariantStats>> {
  const stats: Record<string, VariantStats> = {};
  for (const v of VARIANTS) {
    const doc = await db
      .collection("bandit_stats")
      .doc(contextKey)
      .collection("variants")
      .doc(v)
      .get();
    if (doc.exists) {
      const d = doc.data();
      stats[v] = {
        shows: d?.shows ?? 0,
        successes: d?.successes ?? 0,
        success_add_to_cart: d?.success_add_to_cart ?? 0,
        success_begin_checkout: d?.success_begin_checkout ?? 0,
        success_purchase: d?.success_purchase ?? 0,
      };
    } else {
      stats[v] = {
        shows: 0, successes: 0,
        success_add_to_cart: 0,
        success_begin_checkout: 0,
        success_purchase: 0,
      };
    }
  }
  return stats;
}

/**
 * Escolhe variante via Thompson Sampling ou bootstrap.
 * @param {Record<string, VariantStats>} stats - Stats por variante.
 * @return {string} Variante escolhida.
 */
function chooseBanditVariant(
  stats: Record<string, VariantStats>
): string {
  const totalShows = Object.values(stats)
    .reduce((sum, s) => sum + s.shows, 0);

  // Bootstrap: explorar balanceado ate ter dados suficientes
  if (totalShows < MIN_BOOTSTRAP) {
    let minShows = Infinity;
    let chosen = "B";
    for (const v of VARIANTS) {
      if (stats[v].shows < minShows) {
        minShows = stats[v].shows;
        chosen = v;
      }
    }
    return chosen;
  }

  // Thompson Sampling: Beta(1+successes, 1+shows-successes)
  let bestSample = -1;
  let bestVariant = "B";
  for (const v of VARIANTS) {
    const s = stats[v];
    const alpha = 1 + s.successes;
    const beta = 1 + s.shows - s.successes;
    const sample = sampleBeta(alpha, beta);
    if (sample > bestSample) {
      bestSample = sample;
      bestVariant = v;
    }
  }
  return bestVariant;
}

// ============================================================
// Offer Bandit — helpers
// ============================================================

interface OfferVariantStats {
  shows: number;
  successes: number;
  success_purchase: number;
  net_revenue_sum_cents: number;
}

/**
 * Le stats de offer_bandit_stats/{key}/variants/{O0|O5|O10}.
 * @param {string} offerContextKey - Contexto da oferta.
 * @return {Promise<Record<string, OfferVariantStats>>} Stats.
 */
async function readOfferBanditStats(
  offerContextKey: string
): Promise<Record<string, OfferVariantStats>> {
  const stats: Record<string, OfferVariantStats> = {};
  for (const v of OFFER_VARIANTS) {
    const doc = await db
      .collection("offer_bandit_stats")
      .doc(offerContextKey)
      .collection("variants")
      .doc(v)
      .get();
    if (doc.exists) {
      const d = doc.data();
      stats[v] = {
        shows: d?.shows ?? 0,
        successes: d?.successes ?? 0,
        success_purchase: d?.success_purchase ?? 0,
        net_revenue_sum_cents:
          d?.net_revenue_sum_cents ?? 0,
      };
    } else {
      stats[v] = {
        shows: 0, successes: 0,
        success_purchase: 0,
        net_revenue_sum_cents: 0,
      };
    }
  }
  return stats;
}

// ============================================================
// Propensity, Gating, Timing helpers
// ============================================================

/**
 * Calcula propensity score heuristico v1.
 * @param {object} f - Features comportamentais.
 * @return {{score: number, bucket: string}} Score e bucket.
 */
function computePropensity(f: {
  beginCheckoutClicked: boolean;
  cartItemsCount: number;
  numCartOpens: number;
  removedItemsCount: number;
}): {score: number; bucket: string} {
  let score: number;
  if (f.beginCheckoutClicked) {
    score = 0.85;
  } else if (f.cartItemsCount >= 2) {
    score = 0.60;
  } else if (f.cartItemsCount === 1) {
    score = 0.45;
  } else {
    score = 0.15;
  }

  if (f.numCartOpens >= 2) score += 0.05;
  if (f.removedItemsCount >= 1) score -= 0.05;

  score = Math.max(0.01, Math.min(0.99, score));

  let bucket: string;
  if (score < 0.25) bucket = "p0";
  else if (score < 0.50) bucket = "p1";
  else if (score < 0.75) bucket = "p2";
  else bucket = "p3";

  return {score, bucket};
}

/**
 * Decide gating com base no score.
 * @param {number} score - Propensity score.
 * @return {{gateDecision: string, eligibleOffers: string[]}}
 */
function applyGating(score: number): {
  gateDecision: string;
  eligibleOffers: string[];
} {
  if (score >= 0.75) {
    return {
      gateDecision: "no_offer",
      eligibleOffers: ["O0"],
    };
  } else if (score >= 0.40) {
    return {
      gateDecision: "bandit",
      eligibleOffers: ["O0", "O5"],
    };
  } else {
    return {
      gateDecision: "force_offer",
      eligibleOffers: ["O5", "O10"],
    };
  }
}

/**
 * Decide timing da oferta.
 * @param {number} score - Propensity score.
 * @param {number} timeInCartSec - Tempo no carrinho.
 * @param {number} numCartOpens - Vezes que abriu carrinho.
 * @return {string} Decisao de timing.
 */
function applyTiming(
  score: number,
  timeInCartSec: number,
  numCartOpens: number
): string {
  if (score >= 0.40) return "on_view_cart";
  if (timeInCartSec < 15 && numCartOpens === 1) {
    return "delayed";
  }
  return "on_view_cart";
}

/**
 * Escolhe oferta via Thompson + penalty entre elegiveis.
 * @param {Record<string, OfferVariantStats>} stats - Stats.
 * @param {string[]} eligibleOffers - Ofertas elegiveis.
 * @return {string} Variante escolhida.
 */
function chooseOfferBanditVariant(
  stats: Record<string, OfferVariantStats>,
  eligibleOffers: string[] = [...OFFER_VARIANTS]
): string {
  const eligible = eligibleOffers.length > 0 ?
    eligibleOffers : [...OFFER_VARIANTS];

  const totalShows = eligible.reduce(
    (sum, v) => sum + (stats[v]?.shows ?? 0), 0
  );

  if (totalShows < OFFER_MIN_BOOTSTRAP) {
    let minShows = Infinity;
    let chosen = eligible[0];
    for (const v of eligible) {
      const s = stats[v]?.shows ?? 0;
      if (s < minShows) {
        minShows = s;
        chosen = v;
      }
    }
    return chosen;
  }

  let bestScore = -Infinity;
  let bestVariant = eligible[0];
  for (const v of eligible) {
    const s = stats[v] ?? {
      shows: 0, success_purchase: 0,
    };
    const alpha = 1 + s.success_purchase;
    const beta = 1 + s.shows - s.success_purchase;
    const sampledRate = sampleBeta(alpha, beta);
    const discountRate = OFFER_DISCOUNTS[v] ?? 0;
    const decisionScore =
      sampledRate - OFFER_LAMBDA * discountRate;
    if (decisionScore > bestScore) {
      bestScore = decisionScore;
      bestVariant = v;
    }
  }
  return bestVariant;
}

export const getPriceBandit = onCall(async (request) => {
  const data = request.data as BanditRequest;
  const {
    installationId,
    productId,
    basePriceCents,
    contextKey,
  } = data;

  // 1. Ler stats do bandit para este contexto
  const stats = await readBanditStats(contextKey);

  // 2. Escolher variante (epsilon-greedy + Thompson)
  let variantId: string;
  let policy: string;
  const u = Math.random();
  if (u < EPSILON) {
    // Exploracao aleatoria
    variantId = VARIANTS[Math.floor(Math.random() * VARIANTS.length)];
    policy = "explore";
  } else {
    variantId = chooseBanditVariant(stats);
    policy = "thompson";
  }
  const mult = MULTIPLIERS[variantId];

  // 3. Calcular preco (minimo 1 centavo)
  const priceCents = Math.max(
    1,
    Math.round(basePriceCents * mult)
  );

  // 4. Gerar impression_id e timestamps
  const impressionId = generateUUID();
  const now = Date.now();
  const expiresAt = now + IMPRESSION_TTL_MS;
  const validUntil = now + IMPRESSION_TTL_MS;

  // 5. Gravar impression para atribuicao futura
  await db.collection("price_impressions").doc(impressionId).set({
    impressionId,
    installationId,
    productId,
    contextKey,
    variantId,
    priceCents,
    policy,
    shownAt: now,
    expiresAt,
    purchaseExpiresAt: now + PURCHASE_TTL_MS,
    attributed: false,
    attributed_add_to_cart: false,
    attributed_begin_checkout: false,
    attributed_purchase: false,
  });

  // 6. Incrementar shows atomicamente
  const variantRef = db
    .collection("bandit_stats")
    .doc(contextKey)
    .collection("variants")
    .doc(variantId);

  const variantDoc = await variantRef.get();
  if (variantDoc.exists) {
    await variantRef.update({
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      shows: (variantDoc.data()?.shows ?? 0) + 1,
      updatedAt: now,
    });
  } else {
    await variantRef.set({
      shows: 1,
      successes: 0,
      success_add_to_cart: 0,
      success_begin_checkout: 0,
      success_purchase: 0,
      updatedAt: now,
    });
  }

  // 7. Incrementar agregado diario de shows
  await incrementDailyStat(
    toDayKey(now), `shows_${variantId}`
  );

  return {
    impressionId,
    variantId,
    priceCents,
    validUntil,
    policy,
  };
});

// ============================================================
// recordOutcomeAddToCart — atribuir sucesso ao bandit
// ============================================================

interface OutcomeRequest {
  installationId: string;
  productId: string;
  impressionId: string;
}

export const recordOutcomeAddToCart = onCall(async (request) => {
  const data = request.data as OutcomeRequest;
  const {
    installationId,
    productId,
    impressionId,
  } = data;

  // 1. Ler impression
  const impRef = db
    .collection("price_impressions").doc(impressionId);
  const impDoc = await impRef.get();

  if (!impDoc.exists) {
    return {success: false, reason: "impression_not_found"};
  }

  const imp = impDoc.data()!;

  // 2. Validar
  if (imp.installationId !== installationId) {
    return {success: false, reason: "installation_mismatch"};
  }
  if (imp.productId !== productId) {
    return {success: false, reason: "product_mismatch"};
  }
  if (imp.attributed_add_to_cart === true) {
    return {success: false, reason: "already_attributed_add_to_cart"};
  }
  if (Date.now() > imp.expiresAt) {
    return {success: false, reason: "impression_expired"};
  }

  // 3. Marcar como atribuida
  await impRef.update({
    attributed: true,
    attributed_add_to_cart: true,
  });

  // 4. Incrementar success_add_to_cart atomicamente
  const variantRef = db
    .collection("bandit_stats")
    .doc(imp.contextKey)
    .collection("variants")
    .doc(imp.variantId);

  const variantDoc = await variantRef.get();
  if (variantDoc.exists) {
    await variantRef.update({
      successes: (variantDoc.data()?.successes ?? 0) + 1,
      success_add_to_cart: (variantDoc.data()?.success_add_to_cart ?? 0) + 1,
      updatedAt: Date.now(),
    });
  } else {
    await variantRef.set({
      shows: 0,
      successes: 1,
      success_add_to_cart: 1,
      success_begin_checkout: 0,
      success_purchase: 0,
      updatedAt: Date.now(),
    });
  }

  return {
    success: true,
    contextKey: imp.contextKey,
    variantId: imp.variantId,
  };
});

// ============================================================
// recordOutcomeBeginCheckout — atribuir begin_checkout ao bandit
// ============================================================

interface CheckoutOutcomeRequest {
  installationId: string;
  impressionId: string;
}

export const recordOutcomeBeginCheckout = onCall(async (request) => {
  const data = request.data as CheckoutOutcomeRequest;
  const {installationId, impressionId} = data;

  const impRef = db
    .collection("price_impressions").doc(impressionId);
  const impDoc = await impRef.get();

  if (!impDoc.exists) {
    return {success: false, reason: "impression_not_found"};
  }

  const imp = impDoc.data()!;

  if (imp.installationId !== installationId) {
    return {success: false, reason: "installation_mismatch"};
  }
  if (imp.attributed_begin_checkout === true) {
    return {success: false, reason: "already_attributed_begin_checkout"};
  }
  if (Date.now() > imp.expiresAt) {
    return {success: false, reason: "impression_expired"};
  }

  await impRef.update({attributed_begin_checkout: true});

  const variantRef = db
    .collection("bandit_stats")
    .doc(imp.contextKey)
    .collection("variants")
    .doc(imp.variantId);

  const variantDoc = await variantRef.get();
  if (variantDoc.exists) {
    await variantRef.update({
      success_begin_checkout:
        (variantDoc.data()?.success_begin_checkout ?? 0) + 1,
      updatedAt: Date.now(),
    });
  } else {
    await variantRef.set({
      shows: 0, successes: 0,
      success_add_to_cart: 0,
      success_begin_checkout: 1,
      success_purchase: 0,
      updatedAt: Date.now(),
    });
  }

  return {
    success: true,
    contextKey: imp.contextKey,
    variantId: imp.variantId,
  };
});

// ============================================================
// recordOutcomePurchase — atribuir purchase ao bandit (24h TTL)
// ============================================================

interface PurchaseOutcomeRequest {
  installationId: string;
  impressionId: string;
  valueCents?: number;
  itemsCount?: number;
}

export const recordOutcomePurchase = onCall(async (request) => {
  const data = request.data as PurchaseOutcomeRequest;
  const {installationId, impressionId} = data;

  const impRef = db
    .collection("price_impressions").doc(impressionId);
  const impDoc = await impRef.get();

  if (!impDoc.exists) {
    return {success: false, reason: "impression_not_found"};
  }

  const imp = impDoc.data()!;

  if (imp.installationId !== installationId) {
    return {success: false, reason: "installation_mismatch"};
  }
  if (imp.attributed_purchase === true) {
    return {success: false, reason: "already_attributed_purchase"};
  }
  // Usar janela de 24h para purchase
  const purchaseExpiry = imp.purchaseExpiresAt ??
    (imp.shownAt + PURCHASE_TTL_MS);
  if (Date.now() > purchaseExpiry) {
    return {success: false, reason: "purchase_window_expired"};
  }

  await impRef.update({attributed_purchase: true});

  const variantRef = db
    .collection("bandit_stats")
    .doc(imp.contextKey)
    .collection("variants")
    .doc(imp.variantId);

  const variantDoc = await variantRef.get();
  if (variantDoc.exists) {
    await variantRef.update({
      success_purchase:
        (variantDoc.data()?.success_purchase ?? 0) + 1,
      updatedAt: Date.now(),
    });
  } else {
    await variantRef.set({
      shows: 0, successes: 0,
      success_add_to_cart: 0,
      success_begin_checkout: 0,
      success_purchase: 1,
      updatedAt: Date.now(),
    });
  }

  // Incrementar agregado diario de purchases + receita
  const dayKey = toDayKey(imp.shownAt ?? Date.now());
  await incrementDailyStat(
    dayKey, `purchases_${imp.variantId}`
  );
  if (imp.priceCents) {
    await incrementDailyStat(
      dayKey,
      `revenue_${imp.variantId}`,
      imp.priceCents
    );
  }

  return {
    success: true,
    contextKey: imp.contextKey,
    variantId: imp.variantId,
  };
});

// ============================================================
// getBanditStats — endpoint debug/observabilidade (relatório expandido)
// ============================================================

export const getBanditStats = onRequest(async (req, res) => {
  const contextKey = req.query.contextKey as string | undefined;

  if (contextKey) {
    // Stats de um contexto especifico
    const stats = await readBanditStats(contextKey);
    res.json({contextKey, variants: stats});
    return;
  }

  // Listar todos os contextos com stats
  const contexts = await db.collection("bandit_stats").listDocuments();
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const result: Record<string, any> = {};

  for (const ctxDoc of contexts) {
    const variantDocs = await ctxDoc
      .collection("variants").get();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const variants: Record<string, any> = {};
    variantDocs.forEach((vDoc) => {
      const d = vDoc.data();
      const shows = d.shows ?? 0;
      const pct = (n: number) => shows > 0 ?
        (n / shows * 100).toFixed(1) + "%" : "N/A";
      variants[vDoc.id] = {
        shows,
        success_add_to_cart: d.success_add_to_cart ?? 0,
        success_begin_checkout: d.success_begin_checkout ?? 0,
        success_purchase: d.success_purchase ?? 0,
        rate_add_to_cart: pct(d.success_add_to_cart ?? 0),
        rate_begin_checkout: pct(d.success_begin_checkout ?? 0),
        rate_purchase: pct(d.success_purchase ?? 0),
      };
    });
    result[ctxDoc.id] = variants;
  }

  res.json({banditStats: result});
});

// ============================================================
// Helper: chave do dia para agregados
// ============================================================

/**
 * Retorna data no formato yyyy-mm-dd.
 * @param {number} epochMs - Epoch em ms.
 * @return {string} Data formatada.
 */
function toDayKey(epochMs: number): string {
  const d = new Date(epochMs);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

/**
 * Incrementa stat diaria atomicamente.
 * @param {string} dayKey - Data yyyy-mm-dd.
 * @param {string} field - Campo a incrementar.
 * @param {number} amount - Valor.
 */
async function incrementDailyStat(
  dayKey: string,
  field: string,
  amount = 1
): Promise<void> {
  const ref = db
    .collection("daily_variant_stats").doc(dayKey);
  const doc = await ref.get();
  if (doc.exists) {
    await ref.update({
      [field]: (doc.data()?.[field] ?? 0) + amount,
      updatedAt: Date.now(),
    });
  } else {
    await ref.set({
      [field]: amount,
      updatedAt: Date.now(),
    });
  }
}

// ============================================================
// getSalesVariantSummary — relatorio de vendas por variante
// ============================================================

export const getSalesVariantSummary = onCall(
  async (request) => {
    const now = Date.now();
    const thirtyDays = 30 * 24 * 60 * 60 * 1000;
    const data = request.data || {};
    const endMs = data.end_ms ?
      parseInt(String(data.end_ms), 10) : now;
    const startMs = data.start_ms ?
      parseInt(String(data.start_ms), 10) :
      now - thirtyDays;

    // Tentar usar daily_variant_stats primeiro
    const startDay = toDayKey(startMs);
    const endDay = toDayKey(endMs);

    const dailyDocs = await db
      .collection("daily_variant_stats")
      .where("__name__", ">=", startDay)
      .where("__name__", "<=", endDay)
      .get();

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const agg: Record<string, any> = {
      shows: {A: 0, B: 0, C: 0},
      purchases: {A: 0, B: 0, C: 0},
      revenue_cents: {A: 0, B: 0, C: 0},
    };

    if (!dailyDocs.empty) {
      // Usar agregados diarios
      dailyDocs.forEach((doc) => {
        const d = doc.data();
        for (const v of VARIANTS) {
          agg.shows[v] +=
            d[`shows_${v}`] ?? 0;
          agg.purchases[v] +=
            d[`purchases_${v}`] ?? 0;
          agg.revenue_cents[v] +=
            d[`revenue_${v}`] ?? 0;
        }
      });
    } else {
      // Fallback: ler price_impressions
      const imps = await db
        .collection("price_impressions")
        .where("shownAt", ">=", startMs)
        .where("shownAt", "<=", endMs)
        .get();

      imps.forEach((doc) => {
        const d = doc.data();
        const v = d.variantId as string;
        if (agg.shows[v] !== undefined) {
          agg.shows[v]++;
          if (d.attributed_purchase === true) {
            agg.purchases[v]++;
            agg.revenue_cents[v] +=
              d.priceCents ?? 0;
          }
        }
      });
    }

    // Calcular taxas
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const purchaseRate: Record<string, any> = {};
    for (const v of VARIANTS) {
      purchaseRate[v] = agg.shows[v] > 0 ?
        Math.round(
          agg.purchases[v] / agg.shows[v] * 10000
        ) / 10000 :
        0;
    }

    // Total geral
    const totalShows = Object.values(
      agg.shows as Record<string, number>
    ).reduce((a, b) => a + b, 0);
    const totalPurchases = Object.values(
      agg.purchases as Record<string, number>
    ).reduce((a, b) => a + b, 0);
    const totalRevenue = Object.values(
      agg.revenue_cents as Record<string, number>
    ).reduce((a, b) => a + b, 0);

    return {
      window: {start_ms: startMs, end_ms: endMs},
      shows: agg.shows,
      purchases: agg.purchases,
      purchase_rate: purchaseRate,
      revenue_cents: agg.revenue_cents,
      totals: {
        shows: totalShows,
        purchases: totalPurchases,
        revenue_cents: totalRevenue,
        overall_rate: totalShows > 0 ?
          Math.round(
            totalPurchases / totalShows * 10000
          ) / 10000 :
          0,
      },
      source: dailyDocs.empty ?
        "price_impressions" :
        "daily_variant_stats",
      days_counted: dailyDocs.size,
    };
  }
);

// ============================================================
// getCartOfferBandit — bandit de oferta/cupom no carrinho
// ============================================================

interface OfferBanditRequest {
  installationId: string;
  cartTotalCents: number;
  offerContextKey: string;
  cartItemsCount?: number;
  numCartOpens?: number;
  timeInCartSec?: number;
  removedItemsCount?: number;
  beginCheckoutClicked?: boolean;
}

export const getCartOfferBandit = onCall(async (request) => {
  const data = request.data as OfferBanditRequest;
  const {
    installationId,
    cartTotalCents,
    offerContextKey,
    cartItemsCount = 0,
    numCartOpens = 1,
    timeInCartSec = 0,
    removedItemsCount = 0,
    beginCheckoutClicked = false,
  } = data;

  // 1. Propensity score
  const {score: propensityScore, bucket: propBucket} =
    computePropensity({
      beginCheckoutClicked,
      cartItemsCount,
      numCartOpens,
      removedItemsCount,
    });

  // 2. Gating
  const {gateDecision, eligibleOffers} =
    applyGating(propensityScore);

  // 3. Timing
  const timingDecision = applyTiming(
    propensityScore, timeInCartSec, numCartOpens
  );

  // 4. Contexto com prop_bucket
  const fullCtxKey =
    `${offerContextKey}|${propBucket}`;

  // 5. Escolher variante
  const stats = await readOfferBanditStats(fullCtxKey);

  let variantId: string;
  let policy: string;

  if (gateDecision === "no_offer") {
    variantId = "O0";
    policy = "no_offer";
  } else {
    const u = Math.random();
    if (u < OFFER_EPSILON) {
      const idx = Math.floor(
        Math.random() * eligibleOffers.length
      );
      variantId = eligibleOffers[idx];
      policy = "explore";
    } else {
      variantId = chooseOfferBanditVariant(
        stats, eligibleOffers
      );
      policy = "thompson";
    }
  }

  // 6. Calcular desconto
  const rate = OFFER_DISCOUNTS[variantId] ?? 0;
  const discountCents = Math.round(
    cartTotalCents * rate
  );
  const finalTotalCents = Math.max(
    cartTotalCents - discountCents, 0
  );

  const now = Date.now();
  const offerImpressionId = generateUUID();

  // 7. Gravar impression com campos expandidos
  await db
    .collection("cart_offer_impressions")
    .doc(offerImpressionId)
    .set({
      offerImpressionId,
      installationId,
      offerContextKey: fullCtxKey,
      variantId,
      cartTotalCents,
      discountPercent: rate,
      discountCents,
      finalTotalCents,
      policy,
      propensity_score: propensityScore,
      prop_bucket: propBucket,
      gate_decision: gateDecision,
      eligible_offers: eligibleOffers,
      timing_decision: timingDecision,
      shownAt: now,
      expiresAt: now + OFFER_IMPRESSION_TTL_MS,
      purchaseExpiresAt: now + OFFER_PURCHASE_TTL_MS,
      attributed_purchase: false,
      order_value_cents: 0,
      net_revenue_cents: 0,
    });

  // 8. Incrementar shows
  const varRef = db
    .collection("offer_bandit_stats")
    .doc(fullCtxKey)
    .collection("variants")
    .doc(variantId);
  const varDoc = await varRef.get();
  if (varDoc.exists) {
    const cur = varDoc.data();
    await varRef.update({
      shows: (cur?.shows ?? 0) + 1,
      updatedAt: now,
    });
  } else {
    await varRef.set({
      shows: 1,
      successes: 0,
      success_purchase: 0,
      net_revenue_sum_cents: 0,
      updatedAt: now,
    });
  }

  // 9. Daily stats
  const dayKey = toDayKey(now);
  await incrementDailyStat(
    dayKey, `offer_shows_${variantId}`
  );

  return {
    offerImpressionId,
    variantId,
    discountPercent: rate,
    discountCents,
    finalTotalCents,
    policy,
    timingDecision,
    propBucket,
  };
});

// ============================================================
// recordOfferOutcomePurchase
// ============================================================

export const recordOfferOutcomePurchase = onCall(
  async (request) => {
    const data = request.data as {
      installationId: string;
      offerImpressionId: string;
      valueCents?: number;
    };

    const impRef = db
      .collection("cart_offer_impressions")
      .doc(data.offerImpressionId);
    const impDoc = await impRef.get();

    if (!impDoc.exists) {
      return {
        success: false,
        reason: "offer_impression_not_found",
      };
    }

    const imp = impDoc.data()!;

    if (imp.installationId !== data.installationId) {
      return {
        success: false,
        reason: "installation_mismatch",
      };
    }

    if (imp.attributed_purchase === true) {
      return {
        success: false,
        reason: "already_attributed_purchase",
      };
    }

    const now = Date.now();
    if (now > imp.purchaseExpiresAt) {
      return {
        success: false,
        reason: "offer_purchase_expired",
      };
    }

    // Calcular net revenue
    const orderValueCents = data.valueCents ?? 0;
    const discCents = imp.discountCents ?? 0;
    const netRevenueCents = Math.max(
      orderValueCents - discCents, 0
    );

    // Marcar atribuicao com revenue
    await impRef.update({
      attributed_purchase: true,
      purchasedAt: now,
      order_value_cents: orderValueCents,
      net_revenue_cents: netRevenueCents,
    });

    // Incrementar success_purchase + net revenue
    const ctxKey = imp.offerContextKey as string;
    const varId = imp.variantId as string;
    const varRef = db
      .collection("offer_bandit_stats")
      .doc(ctxKey)
      .collection("variants")
      .doc(varId);
    const varDoc = await varRef.get();
    if (varDoc.exists) {
      const cur = varDoc.data();
      await varRef.update({
        success_purchase:
          (cur?.success_purchase ?? 0) + 1,
        successes: (cur?.successes ?? 0) + 1,
        net_revenue_sum_cents:
          (cur?.net_revenue_sum_cents ?? 0) +
            netRevenueCents,
        updatedAt: now,
      });
    }

    // Daily stats
    const dayKey = toDayKey(now);
    await incrementDailyStat(
      dayKey, `offer_purchases_${varId}`
    );
    if (orderValueCents > 0) {
      await incrementDailyStat(
        dayKey,
        `offer_revenue_${varId}`,
        orderValueCents
      );
      await incrementDailyStat(
        dayKey,
        `offer_net_revenue_${varId}`,
        netRevenueCents
      );
    }

    return {
      success: true,
      offerContextKey: ctxKey,
      variantId: varId,
      netRevenueCents,
    };
  }
);

// ============================================================
// getOfferSummary — relatorio de ofertas por variante
// ============================================================

export const getOfferSummary = onCall(
  async (request) => {
    const now = Date.now();
    const thirtyDays = 30 * 24 * 60 * 60 * 1000;
    const data = request.data || {};
    const endMs = data.end_ms ?
      parseInt(String(data.end_ms), 10) : now;
    const startMs = data.start_ms ?
      parseInt(String(data.start_ms), 10) :
      now - thirtyDays;

    const startDay = toDayKey(startMs);
    const endDay = toDayKey(endMs);

    const dailyDocs = await db
      .collection("daily_variant_stats")
      .where("__name__", ">=", startDay)
      .where("__name__", "<=", endDay)
      .get();

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const agg: Record<string, any> = {
      shows: {O0: 0, O5: 0, O10: 0},
      purchases: {O0: 0, O5: 0, O10: 0},
      net_revenue: {O0: 0, O5: 0, O10: 0},
    };

    if (!dailyDocs.empty) {
      dailyDocs.forEach((doc) => {
        const d = doc.data();
        for (const v of OFFER_VARIANTS) {
          agg.shows[v] +=
            d[`offer_shows_${v}`] ?? 0;
          agg.purchases[v] +=
            d[`offer_purchases_${v}`] ?? 0;
          agg.net_revenue[v] +=
            d[`offer_net_revenue_${v}`] ?? 0;
        }
      });
    } else {
      // Fallback: scan cart_offer_impressions
      const imps = await db
        .collection("cart_offer_impressions")
        .where("shownAt", ">=", startMs)
        .where("shownAt", "<=", endMs)
        .get();

      imps.forEach((doc) => {
        const d = doc.data();
        const v = d.variantId as string;
        if (agg.shows[v] !== undefined) {
          agg.shows[v]++;
          if (d.attributed_purchase === true) {
            agg.purchases[v]++;
            agg.net_revenue[v] +=
              d.net_revenue_cents ?? 0;
          }
        }
      });
    }

    // Taxas
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const purchaseRate: Record<string, any> = {};
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const netRevPerShow: Record<string, any> = {};
    for (const v of OFFER_VARIANTS) {
      purchaseRate[v] = agg.shows[v] > 0 ?
        Math.round(
          agg.purchases[v] /
            agg.shows[v] * 10000
        ) / 10000 :
        0;
      netRevPerShow[v] = agg.shows[v] > 0 ?
        Math.round(
          agg.net_revenue[v] / agg.shows[v]
        ) :
        0;
    }

    const totalShows = Object.values(
      agg.shows as Record<string, number>
    ).reduce((a, b) => a + b, 0);
    const totalPurchases = Object.values(
      agg.purchases as Record<string, number>
    ).reduce((a, b) => a + b, 0);
    const totalNetRevenue = Object.values(
      agg.net_revenue as Record<string, number>
    ).reduce((a, b) => a + b, 0);

    return {
      window: {start_ms: startMs, end_ms: endMs},
      shows: agg.shows,
      purchases: agg.purchases,
      purchase_rate: purchaseRate,
      net_revenue_cents: agg.net_revenue,
      net_rev_per_show: netRevPerShow,
      totals: {
        shows: totalShows,
        purchases: totalPurchases,
        net_revenue_cents: totalNetRevenue,
        overall_rate: totalShows > 0 ?
          Math.round(
            totalPurchases /
              totalShows * 10000
          ) / 10000 :
          0,
      },
      source: dailyDocs.empty ?
        "cart_offer_impressions" :
        "daily_variant_stats",
      days_counted: dailyDocs.size,
    };
  }
);

// ============================================================
// simulateBanditData — gerar dados simulados em volume
// ============================================================

/**
 * Cenarios de conversao por variante e contexto.
 * Padroes realistas:
 * - Preco baixo (A) converte mais em dispositivos low
 * - Preco base (B) converte bem em mid
 * - Preco alto (C) converte em high (menos sensivel)
 * - Carrinho cheio (cart3p) tem checkout/purchase maior
 */
/**
 * Retorna taxas de conversao simuladas por contexto.
 * @param {string} variant - Variante (A/B/C).
 * @param {string} tier - Device tier (low/mid/high).
 * @param {string} cartBucket - Estado do carrinho.
 * @return {{addToCart:number,checkout:number,purchase:number}}
 */
function getConversionRates(
  variant: string,
  tier: string,
  cartBucket: string
): {addToCart: number; checkout: number; purchase: number} {
  // Taxas base por variante
  const base: Record<string, {
    addToCart: number;
    checkout: number;
    purchase: number;
  }> = {
    "A": {addToCart: 0.45, checkout: 0.25, purchase: 0.12},
    "B": {addToCart: 0.35, checkout: 0.20, purchase: 0.10},
    "C": {addToCart: 0.25, checkout: 0.15, purchase: 0.08},
  };

  const r = {...base[variant]};

  // Ajuste por tier
  if (tier === "low") {
    // Sensivel a preco: A converte muito mais
    if (variant === "A") {
      r.addToCart += 0.15;
      r.purchase += 0.05;
    }
    if (variant === "C") {
      r.addToCart -= 0.10;
      r.purchase -= 0.04;
    }
  } else if (tier === "high") {
    // Menos sensivel: C converte quase igual
    if (variant === "C") {
      r.addToCart += 0.10;
      r.purchase += 0.04;
    }
  }

  // Ajuste por cart_bucket (mais itens = mais engajado)
  if (cartBucket === "cart1_2") {
    r.checkout += 0.10;
    r.purchase += 0.05;
  } else if (cartBucket === "cart3p") {
    r.checkout += 0.20;
    r.purchase += 0.12;
  }

  // Clamp entre 0 e 1
  r.addToCart = Math.max(0, Math.min(1, r.addToCart));
  r.checkout = Math.max(0, Math.min(1, r.checkout));
  r.purchase = Math.max(0, Math.min(1, r.purchase));

  return r;
}

export const simulateBanditData = onRequest(
  async (req, res) => {
    const showsPerCell = parseInt(
      req.query.shows as string || "100", 10
    );

    const ufs = ["SP", "RJ", "MG", "BA", "RS"];
    const tiers = ["low", "mid", "high"];
    const days = [5, 12, 20, 26];
    const carts = ["cart0", "cart1_2", "cart3p"];

    let totalContexts = 0;
    let totalShows = 0;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const writes: Array<{ref: any; data: any}> = [];
    const now = Date.now();

    for (const uf of ufs) {
      for (const tier of tiers) {
        for (const day of days) {
          for (const cart of carts) {
            const ctxKey =
              `${uf}|${tier}|day_${day}|${cart}`;
            totalContexts++;

            for (const v of VARIANTS) {
              const rates = getConversionRates(
                v, tier, cart
              );

              // Sortear com variancia +-15%
              const shows = showsPerCell +
                Math.floor(
                  (Math.random() - 0.5) *
                  showsPerCell * 0.3
                );
              let addToCart = 0;
              let beginCheckout = 0;
              let purchase = 0;

              for (let i = 0; i < shows; i++) {
                if (Math.random() < rates.addToCart) {
                  addToCart++;
                  if (Math.random() < rates.checkout) {
                    beginCheckout++;
                    if (
                      Math.random() < rates.purchase
                    ) {
                      purchase++;
                    }
                  }
                }
              }

              writes.push({
                ref: db
                  .collection("bandit_stats")
                  .doc(ctxKey)
                  .collection("variants")
                  .doc(v),
                data: {
                  shows,
                  successes: addToCart,
                  success_add_to_cart: addToCart,
                  success_begin_checkout: beginCheckout,
                  success_purchase: purchase,
                  updatedAt: now,
                },
              });
              totalShows += shows;
            }
          }
        }
      }
    }

    // Gerar daily_variant_stats (30 dias)
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const dailyAgg: Record<string, any> = {};
    const msPerDay = 24 * 60 * 60 * 1000;
    for (let d = 0; d < 30; d++) {
      const dayMs = now - d * msPerDay;
      const dayKey = toDayKey(dayMs);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const dayStat: Record<string, any> = {
        updatedAt: now,
      };
      for (const v of VARIANTS) {
        // Distribuir shows/purchases por dia
        const dayShows = Math.floor(
          totalShows / 3 / 30 +
          (Math.random() - 0.5) * 20
        );
        // Usar taxas medias para gerar purchases
        const dayPurchases = Math.floor(
          dayShows * (
            v === "A" ? 0.08 :
              v === "B" ? 0.06 : 0.05
          ) +
          (Math.random() - 0.5) * 3
        );
        const avgPrice = v === "A" ? 9000 :
          v === "B" ? 10000 : 11000;
        dayStat[`shows_${v}`] =
          Math.max(0, dayShows);
        dayStat[`purchases_${v}`] =
          Math.max(0, dayPurchases);
        dayStat[`revenue_${v}`] =
          Math.max(0, dayPurchases) * avgPrice;
      }
      dailyAgg[dayKey] = dayStat;
    }

    // Adicionar daily stats aos writes
    for (const [dayKey, data] of
      Object.entries(dailyAgg)) {
      writes.push({
        ref: db
          .collection("daily_variant_stats")
          .doc(dayKey),
        data,
      });
    }

    // Escrever em batches de 490
    for (let i = 0; i < writes.length; i += 490) {
      const chunk = writes.slice(i, i + 490);
      const batch = db.batch();
      for (const w of chunk) {
        batch.set(w.ref, w.data);
      }
      await batch.commit();
    }

    res.json({
      success: true,
      totalContexts,
      totalVariantCells: totalContexts * 3,
      totalShows,
      showsPerCell,
      dailyStatsDays: 30,
      ufs,
      tiers,
      days: days.map((d) => `day_${d}`),
      cartBuckets: carts,
      note: "Padroes: A converte melhor em low-tier, " +
        "C converte melhor em high-tier, " +
        "cart3p tem mais checkout/purchase. " +
        "Daily stats gerados para 30 dias.",
    });
  }
);
