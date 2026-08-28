# Jive Animation Pipeline

Wewnętrzny pipeline do programowego generowania animacji maskotki Jive. Jedynym
źródłowym assetem wszystkich generatorów jest `source/jive.png`. Plik ma pozostać
niezmieniony; każda klatka jest renderowana bezpośrednio z niego.

## Instalacja

```bash
cd tools/jive-animation
python -m pip install -r requirements.txt
```

## Tryby renderowania

- `prototype`: 12 FPS, oversampling 2×, szybki eksport lossy i podstawowa
  walidacja. Służy do szybkiej oceny ruchu.
- `final`: 25 FPS, oversampling 8×, bezstratny eksport i pełna walidacja
  właściwa dla danego generatora.

## Prototype

```bash
python generate_jive_blink.py --mode prototype
python generate_jive_look.py --mode prototype
python generate_jive_breath.py --mode prototype
```

Wyniki trafiają odpowiednio do:

- `output/jive_blink_preview.webp`
- `output/jive_look_preview.webp`
- `output/jive_breath_internal_test.webp`

## Final

```bash
python generate_jive_blink.py --mode final
python generate_jive_look.py --mode final
python generate_jive_breath.py --mode final
```

Wyniki trafiają odpowiednio do:

- `output/jive_blink.webp`
- `output/jive_look.webp`
- `output/jive_breath.webp`

Można jawnie podać alternatywne ścieżki przez `--source` i `--output`, ale
domyślnym oraz kanonicznym źródłem pozostaje `source/jive.png`. Pipeline niczego
nie kopiuje automatycznie do zasobów aplikacji Android.

Katalog `.cache/` jest przeznaczony wyłącznie na odtwarzalne dane pochodne,
takie jak maski i bounding boxy. Nie jest drugim źródłem assetu.
