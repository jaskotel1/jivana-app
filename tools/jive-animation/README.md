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

## Konwersja filmu AI do transparentnej animacji

Konwerter zachowuje rozdzielczość, pozycję postaci i czas filmu. Usuwa tylko
prawie białe tło połączone z krawędzią obrazu, zamknięte białe komponenty w
dolnej części sylwetki oraz — przy pewnym rozpoznaniu — jasnolawendowy cień pod
stopami. Nie kopiuje wyniku do zasobów aplikacji.

```bash
python video_to_jive_animation.py path/to/jive-idle.mp4 \
  --every-nth-frame 2 \
  --background-threshold 245 \
  --remove-shadow true \
  --body-color "#A8D8A0" \
  --anchors path/to/anchors.json \
  --debug \
  --preview
```

Podanie `--body-color` jest opcjonalne. Zmiana koloru wykorzystuje przestrzeń
LAB: zachowuje luminancję, lokalne cienie i zróżnicowanie chromy, a modyfikuje
wyłącznie piksele maski lawendowego futra. Oczy, zęby, usta i kontury nie należą
do tej maski.

Plik anchorów jest opcjonalnym obiektem JSON z polem `anchors`. Musi zawierać
punkty `head`, `eyes`, `body`, `left_hand`, `right_hand`, `left_foot` i
`right_foot`; każdy punkt ma postać dwuelementowej tablicy `[x, y]`. Współrzędne
są definiowane ręcznie dla pierwszej klatki i na obecnym etapie powielane do
wszystkich klatek bez automatycznego śledzenia anatomii.

Wynik trafia do katalogu nazwanego jak animacja. Sufiks wejścia `-source` jest
pomijany, więc `jive-idle-source.mp4` tworzy `output/jive-idle/`. Transparentne
klatki są w `frames/`, maski koloru w `masks/body/`, animacja w
`jive-idle.webp`, dane czasu i anchorów w `metadata.json`, a dodatkowe maski
diagnostyczne w `debug/`. `preview.png` powstaje domyślnie; można go wyłączyć
przez `--no-preview`.

Struktura `accessories/` rezerwuje niezależne assety dla slotów `head`, `face`,
`body` i `feet`. Format przyszłych metadanych akcesorium opisuje
`accessories/README.md`; konwerter nie renderuje jeszcze akcesoriów.

Pełną listę parametrów pokazuje `python video_to_jive_animation.py --help`.
