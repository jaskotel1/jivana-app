# Jive accessory assets

This directory reserves independent transparent assets for future Jive
accessories. The base animation must never have an accessory baked into it.

Supported slots are `head`, `face`, `body`, and `feet`. Each future asset should
have metadata compatible with this shape:

```json
{
  "id": "stable_unique_id",
  "slot": "face",
  "anchor": "eyes",
  "offsetX": 0,
  "offsetY": 0,
  "scale": 1.0,
  "rotation": 0
}
```

The `anchor` value refers to an anchor stored in an animation's `metadata.json`.
Rendering, equip/unequip behavior, and Android integration are intentionally out
of scope at this stage.
