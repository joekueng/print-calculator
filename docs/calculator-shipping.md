# Calculator shipping

Calculator quotes use customer prices CHF 2 / 4 / 9 / 12 / 25. Shop carts retain their existing calculation. Domestic automatic shipping is limited to CH. These are selling prices, not live Swiss Post API rates or discounted label costs.

## Packing and geometry

After slicing, binary/ASCII STL geometry is inspected once. For 3MF uploads the existing persistent STL conversion is used. The inspector tests the original axes and up to 32 triangle-derived orthonormal frames. Every vertex is included in each frame's bounds; dimensions round outward to 0.001 mm. Geometry inspection is bounded to 300,000 vertices and 40 MB; larger, unreadable or legacy models use the existing slicer bounding box. Recalculate an old quote to obtain geometry-based frames. Printing orientation and slicing costs are not modified.

Each frame receives `shipping.padding-mm` on every side (default 3 mm). All copies are packed as protected rectangular boxes, using six axis permutations and three disjoint-space subdivision orders. The first successful package in price order is chosen. This conservative heuristic never nests objects or overlaps their protective boxes, but can miss a feasible arrangement. It does not guarantee a global minimum. Over 500 total copies or failure to fit requires a manual quote; there is no automatic multiple-parcel or bulky-goods charge.

## Package configuration

Override `shipping.profiles` (`SHIPPING_PROFILES`) and `shipping.padding-mm` (`SHIPPING_PADDING_MM`) through Spring configuration. Profiles are separated by semicolons; each has ten comma-separated fields:

```text
code,innerX,innerY,innerZ,outerX,outerY,outerZ,packagingGrams,maxPackedGrams,priceChf
```

Lengths are mm, weights g. `packagingGrams` must include the empty container and protective packing. Internal dimensions exclude container walls; the per-object padding is applied separately. Add multiple box sizes at the same price if needed. Configuration validates positive finite measurements, internal versus external size and the postal size/weight limits for the five prices.

Initial profiles are **nominal tariff envelopes, not verified stocked packaging**:

| Profile | Internal mm | External mm | Packaging g | Packed limit g | CHF |
| --- | --- | --- | ---: | ---: | ---: |
| LETTER | 351 × 248 × 18 | 353 × 250 × 20 | 20 | 1,000 | 2 |
| SMALL | 248 × 174 × 48 | 250 × 176 × 50 | 40 | 500 | 4 |
| PARCEL_2 | 990 × 590 × 590 | 1,000 × 600 × 600 | 300 | 2,000 | 9 |
| PARCEL_10 | 990 × 590 × 590 | 1,000 × 600 × 600 | 300 | 10,000 | 12 |
| PARCEL_30 | 990 × 590 × 590 | 1,000 × 600 × 600 | 500 | 30,000 | 25 |

Replace these allowances with measured packaging before relying on margins in production, especially the large parcel packaging weights. No packaging purchase or stock availability is implied. Example for a stocked smaller parcel box (repeat at other weight tiers as appropriate):

```properties
shipping.padding-mm=3
shipping.profiles=LETTER,351,248,18,353,250,20,20,1000,2;SMALL,248,174,48,250,176,50,40,500,4;BOX_S,290,190,90,300,200,100,150,2000,9
```

Packed weight equals the sum of sliced material grams times quantity plus packaging grams. Slicer weight may include discarded supports/purge; it remains an estimate. Do not interpret the margin allowance as sufficient protection for every fragile shape.

## API, checkout and persistence

Session responses add `shippingQuote` with status (`QUOTED`, `NOT_REQUIRED`, `PENDING`, `MANUAL_QUOTE`), cost, package measurements, packed weight, padding and placements. Each placement identifies the quote item/copy, protected dimensions, position and orientation/rotation indices. Item responses provide `shippingOrientations` (dimensions, basis rows, projected minima). The original bounds are used when those frames are absent. A future preview can use this data; no 3D packing viewer is included.

Rotation indices enumerate XYZ, XZY, YXZ, YZX, ZXY, ZYX. When building a proper rotation matrix for a future viewer, flip one axis for odd permutations and translate using the corresponding maximum rather than minimum; an axis permutation alone can be a reflection.

An unavailable quote has a zero placeholder amount, **not free shipping**. The calculator/checkout hide its shipping price, explain that the total excludes shipping and disable ordering. The server independently blocks creation. CAD-only/empty sessions have `NOT_REQUIRED`. Updating quantities recomputes the shipment. Checkout sends `expectedShippingCostChf`; the backend rejects a changed price so it can be reviewed before submission.

Orders snapshot the calculation and algorithm version in nullable JSON `orders.shipping_quote_snapshot`, in addition to the existing shipping amount. Existing orders are not repriced. This is an additive schema change under Hibernate `ddl-auto=update`; `db.sql` includes the column for fresh databases. For managed production migrations use:

```sql
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_quote_snapshot jsonb;
```

No migration was run against a database. Deploy backend and frontend together and verify representative real packages. New calculations use the new algorithm immediately; there is no feature flag or automatic tariff refresh.

Postal references checked 6 September 2026: [B Mail](https://www.post.ch/en/sending-letters/domestic-letters/b-mail-letter), [Economy parcels](https://www.post.ch/en/sending-parcels/domestic-parcels/postpac-economy). CHF 2 and CHF 4 use B Mail services without tracking; parcel prices are the agreed customer tiers.
