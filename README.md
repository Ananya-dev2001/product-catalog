# Product Catalog Service

A REST backend for an e-commerce product catalog: create/read/update/delete products,
plus filtered, paginated listing. Built for the stated constraints:

- **Scale**: a few hundred thousand products.
- **Access pattern**: reads vastly outnumber writes (catalog browsing >> catalog editing).
- **Query needs**: list/filter by category and price range, with pagination.

**Stack**: Java 25, Spring Boot 3, MongoDB, Maven. (No frontend is included — the task
asks specifically for the backend; happy to add a thin React admin UI on top if useful.)

---

## Running it

```bash
docker-compose up --build
```

This starts MongoDB and the API together. The API is then available at
`http://localhost:8080`, with interactive docs at `http://localhost:8080/swagger-ui.html`.

To run against a local Mongo instead: `mvn spring-boot:run` with `MONGODB_URI` pointing
at your instance (defaults to `mongodb://localhost:27017/catalog`).

**Tests**: `mvn test` runs unit tests (Mockito) and an integration test suite that spins
up a real MongoDB via Testcontainers and exercises the full HTTP API, including a
pagination test that walks every page and asserts no duplicates/gaps. Testcontainers
needs Docker available on the machine running the tests.

> Note on how this was built: I don't have network access to Maven Central in the
> sandbox I used to write this, so I wasn't able to run `mvn test` myself in that
> environment. I've reasoned through the code carefully and the integration test
> covers the main paths, but please run the test suite locally as a first step.

---

## Data model

Single `products` collection, one document per product:

```json
{
  "_id": "ObjectId",
  "sku": "MECH-KB-01",
  "name": "Mechanical Keyboard",
  "description": "...",
  "category": "electronics",
  "price": "89.99",
  "currency": "USD",
  "stockQuantity": 50,
  "attributes": { "color": "black", "switchType": "brown" },
  "imageUrls": ["https://..."],
  "active": true,
  "createdAt": "...",
  "updatedAt": "...",
  "version": 3
}
```

**Why MongoDB (document store) over a relational database:**
- Reads dominate, and the read path is "fetch a product" or "list products matching
  simple filters" — never a multi-table join. A document store lets each read be
  satisfied by one lookup/scan against one collection.
- Products aren't uniform: a T-shirt has size/color, a laptop has RAM/storage, a book
  has ISBN/author. An `attributes` map absorbs that variability without an EAV table or
  constant schema migrations, which a relational schema would otherwise need.
- The catalog is a bounded, non-relational-heavy dataset (no complex multi-entity
  transactions across products) — exactly where Mongo's simplicity pays off and its
  weaker cross-document transaction story doesn't bite.

Trade-off acknowledged: if this service later needed strong relational integrity (e.g.
enforcing referential links to a large, independently-changing supplier/orders schema
with multi-entity transactions), a relational database would be the better fit. For a
catalog read path, it isn't.

**Money as BigDecimal → BSON Decimal128**: prices are never stored as `double`/`float`;
a custom converter (`MongoConfig`) maps `BigDecimal` to `Decimal128` to avoid floating
point rounding errors in prices.

**Soft delete**: `DELETE` sets `active=false` rather than removing the document. Keeps
historical references (past orders, analytics) intact and is a cheap filter on the read
path rather than a hard delete that could orphan other data.

**Optimistic locking**: `@Version` on the document means a stale concurrent update fails
loudly (`OptimisticLockingFailureException`) instead of silently overwriting a newer
write — cheap insurance given writes, while rare, still come from multiple admins/
integrations.

---

## Indexing strategy

Created on startup in `MongoIndexConfig` (in a real production rollout I'd move this to
a versioned migration tool like Mongock, but startup-time `ensureIndex` keeps this
exercise runnable with one command):

| Index | Purpose |
|---|---|
| `{ sku: 1 }` unique | Enforce SKU uniqueness; fast SKU lookup |
| `{ active: 1, category: 1, price: 1, _id: 1 }` | Primary listing index — serves "active products in category X, price between A and B, sorted by price" as an index-only range scan |
| `{ active: 1, price: 1, _id: 1 }` | Same, for listings without a category filter |

The `_id` tail on both compound indexes exists specifically to support cursor pagination
(next section) as a tiebreaker for products sharing the same price.

At a few hundred thousand documents these compound indexes comfortably fit in memory,
so filtered listing queries stay fast without a caching layer. If the catalog grew an
order of magnitude larger, a read-through cache (Redis) in front of the most common
category/price-bucket queries would be the next lever, since reads dominate writes by
assumption.

---

## Pagination: cursor-based, not offset-based

The listing endpoint uses **keyset (cursor) pagination** — sort by `(price, _id)`, and
each response includes an opaque `nextCursor` token for the next page — rather than
classic `?page=3&size=20` offset pagination. Reasoning:

- Offset pagination in MongoDB (`skip(n).limit(k)`) requires walking and discarding the
  first `n` documents on every request. At a few hundred thousand products, a client
  paging deep into filtered results (page 500 of a popular category) would get
  progressively slower — the exact failure mode a read-heavy service can't afford.
- Cursor pagination resumes exactly where the last page left off via an indexed range
  condition (`price > cursor.price OR (price == cursor.price AND _id > cursor.id)`), so
  every page costs the same regardless of how deep the client is.
- The trade-off is that clients can't jump straight to "page 47" or see a live total
  count cheaply. For a product catalog's primary UX (browse/scroll, infinite-scroll or
  "load more"), that's the right trade — and it's the same approach Stripe, GitHub, and
  most high-scale list APIs use for exactly this reason. If arbitrary page-jumping were
  a hard requirement, I'd add a separate, cached/precomputed count and accept the offset
  cost only for that specific need rather than for every list call.

The service also fetches `limit + 1` rows internally so it can report `hasMore` without
a second query.

---

## API

Base path: `/api/v1/products`. Full interactive spec at `/swagger-ui.html` once running.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/products` | Create a product |
| `GET` | `/api/v1/products/{id}` | Get a product by id |
| `PUT` | `/api/v1/products/{id}` | Replace a product's fields |
| `DELETE` | `/api/v1/products/{id}` | Soft-delete (deactivate) a product |
| `GET` | `/api/v1/products` | List/filter products, cursor-paginated |

### `GET /api/v1/products` query params

| Param | Description |
|---|---|
| `category` | Exact category match |
| `minPrice`, `maxPrice` | Inclusive price range |
| `cursor` | Opaque token from a previous response's `nextCursor` |
| `limit` | Page size, 1–100, default 20 |

Example:

```
GET /api/v1/products?category=electronics&minPrice=20&maxPrice=100&limit=20
```

```json
{
  "items": [ { "id": "...", "sku": "...", "name": "...", "price": "29.99", "...": "..." } ],
  "nextCursor": "MjkuOTl8NjZmMWY3...",
  "hasMore": true,
  "limit": 20
}
```

Pass `nextCursor` back as `cursor` to fetch the next page.

### Error format

```json
{ "timestamp": "...", "status": 404, "error": "Product not found: <id>" }
```

`400` for validation errors (includes a `fieldErrors` map), `404` not found, `409`
duplicate SKU.

---

## What I'd do with more time

- **Search beyond exact category match**: a text index or an external search engine
  (OpenSearch/Elasticsearch) for free-text product search — out of scope here since the
  task calls out category/price filtering specifically, but a natural next filter.
- **Bulk write endpoints** for catalog imports, since single-document writes don't fit a
  "load 50k SKUs from a supplier feed" use case well.
- **Rate limiting / API key auth** at the gateway level — not modeled here since auth
  strategy wasn't specified.
- **Category as its own small collection** if category hierarchies (parent/child
  categories, renaming) become a requirement — currently a flat string for simplicity.
- **Contract tests / OpenAPI-generated client** for frontend integration.
