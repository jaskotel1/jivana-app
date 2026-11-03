# Jive — raport usunięcia starego renderera

Data: 3 listopada 2026.

## Zakres i zabezpieczenie pracy

Docelowo Home w DEV i PROD używa jednego renderera Compose Canvas.
Playground pozostaje ekranem wyłącznie DEV. Nie dodano mapowania kondycji
na animacje, nowych stanów, strojów, zależności ani zmian w bazie danych.

Przed implementacją sprawdzono `git status`, w tym wszystkie nieśledzone pliki.
Zidentyfikowano 14 plików wcześniejszej pracy nad Jive 2.0 oraz dwie zmiany IDE.
Ich treść, patche śledzonych zmian, status i manifesty SHA-256 skopiowano
do lokalnego katalogu kopii poza repozytorium. Zachowano też 12 podglądów
Playground do porównania wyglądu. Nie wykonywano commita, resetu ani clean.
Zmiany `.idea/.name` i `.idea/deploymentTargetSelector.xml` pozostawiono bez edycji.

## Integracja Home

Przepływ `Room → repozytorium → ObserveJiveConditionUseCase → ViewModel/Controller
→ MainActivity → AppNavHost → HomeScreen(condition)` pozostaje zachowany.
Parametr kondycji nie steruje jeszcze wyglądem. Home tworzy własny animator
Idle i używa `JiveWardrobe.Default`; zmiana kondycji nie restartuje animatora.
Zachowano układ Home, wyśrodkowanie i kolejność dotychczasowych modyfikatorów
rozmiaru, łącznie z kwadratowym kontenerem.

`JiveAnimationClock` wydzielono z Playground. Jego logikę porównano z pierwowzorem
po pominięciu różnicy widoczności funkcji i kwalifikacji importu.
Pozostała identyczna: zegar działa w `RESUMED`, anuluje się po opuszczeniu
kompozycji, a po dezaktywacji wznawia od zatrzymanego czasu. Home i Playground
korzystają z odrębnych instancji animatora, lecz tego samego kodu zegara.

Przed usunięciem starych plików przeszły buildy i testy jednostkowe obu wariantów
oraz testy emulatorowe: 7 DEV (Home i Playground), 4 PROD (Home i nawigacja).
Obejrzano zrzuty Home obu wariantów. Wszystkie 12 PNG Playground było identycznych
bajt w bajt z kopią sprzed migracji. Dopiero potem usunięto stary system.

## Przeniesione pliki

Wszystkie ścieżki w tym raporcie są względne wobec repozytorium.

Bez zmiany treści przeniesiono z `app/src/dev/java/` do `app/src/main/java/`:

```text
com/uniccomj/jivana/presentation/jiveplayground/JivePose.kt
com/uniccomj/jivana/presentation/jiveplayground/JiveAnimator.kt
com/uniccomj/jivana/presentation/jiveplayground/JiveGeometry.kt
com/uniccomj/jivana/presentation/jiveplayground/JiveCharacter.kt
com/uniccomj/jivana/presentation/jiveplayground/JiveOutfit.kt
```

Test silnika przeniesiono bez zmiany treści:

```text
app/src/testDev/java/com/uniccomj/jivana/presentation/jiveplayground/JiveAnimatorTest.kt
→ app/src/test/java/com/uniccomj/jivana/presentation/jiveplayground/JiveAnimatorTest.kt
```

Pakiet Kotlin zachowano, aby uniknąć niepotrzebnych zmian importów i API.
Geometria, proporcje, paleta, ubrania, interpolacja i parametry animacji
pozostają zgodne z pierwowzorem; sprawdzono sumy SHA-256 pięciu plików silnika.

## Dodane i zmodyfikowane pliki

Dodane:

```text
app/src/main/java/com/uniccomj/jivana/presentation/jiveplayground/JiveAnimationClock.kt
app/src/main/java/com/uniccomj/jivana/presentation/components/JiveHomeCharacter.kt
app/src/androidTest/java/com/uniccomj/jivana/presentation/home/JiveHomeScreenTest.kt
app/src/androidTestProd/java/com/uniccomj/jivana/presentation/navigation/ProdNavigationTest.kt
docs/development/jive-cleanup.md
```

Zmodyfikowane podczas cleanupu:

```text
app/src/main/java/com/uniccomj/jivana/presentation/home/HomeScreen.kt
app/src/dev/java/com/uniccomj/jivana/presentation/jiveplayground/JivePlaygroundScreen.kt
app/src/prod/java/com/uniccomj/jivana/presentation/navigation/DevelopmentDestinations.kt
app/src/androidTest/java/com/uniccomj/jivana/ComposeUiTest.kt
app/src/androidTestDev/java/com/uniccomj/jivana/presentation/jiveplayground/JivePlaygroundScreenTest.kt
README.md
docs/development/setup.md
docs/development/jive-playground.md
```

W Playground usunięto wyłącznie lokalną definicję zegara i zbędne importy.
W teście nawigacji pozostawiono ręczny zegar również po powrocie na Home,
ponieważ Home ma teraz ciągłą animację Compose. Z `ComposeUiTest` usunięto
tylko metodę sprawdzającą stary renderer i jej importy, zachowując test tekstu.
Zmiana pliku nawigacji PROD dotyczy tylko komentarza opisującego source sety.

## Usunięte pliki — 21

Poniższa lista stanowi historię migracji, a nie aktywne odwołania w kodzie:

```text
app/src/main/res/drawable/jive_idle.webp
app/src/main/java/com/uniccomj/jivana/presentation/components/JiveIdle.kt
app/src/main/java/com/uniccomj/jivana/presentation/avatar/JiveAnimationResolver.kt
app/src/test/java/com/uniccomj/jivana/presentation/avatar/JiveAnimationResolverTest.kt
tools/jive-animation/.gitignore
tools/jive-animation/accessories/body/.gitkeep
tools/jive-animation/accessories/face/.gitkeep
tools/jive-animation/accessories/feet/.gitkeep
tools/jive-animation/accessories/head/.gitkeep
tools/jive-animation/accessories/README.md
tools/jive-animation/generate_jive_blink.py
tools/jive-animation/generate_jive_breath.py
tools/jive-animation/generate_jive_idle.py
tools/jive-animation/generate_jive_look.py
tools/jive-animation/jive_animation_core.py
tools/jive-animation/optimize_jive_animation_webp.py
tools/jive-animation/output/.gitkeep
tools/jive-animation/README.md
tools/jive-animation/requirements.txt
tools/jive-animation/source/jive.png
tools/jive-animation/video_to_jive_animation.py
```

Przed usunięciem sprawdzono, że te pliki nie zawierały niezapisanej w Git pracy
oraz że katalog narzędzi nie zawierał dodatkowych lokalnych plików poza audytem.

## Wyniki końcowe

| Sprawdzenie | Wynik |
| --- | --- |
| `testDevDebugUnitTest` | 58 testów, 0 błędów; w tym 9 wspólnych testów silnika Canvas. |
| `testProdDebugUnitTest` | 58 testów, 0 błędów; ten sam zestaw testów silnika. |
| Android DEV, Pixel 7 / Android 17 | 22 testy, 0 błędów. |
| Android PROD, Pixel 7 / Android 17 | 19 testów, 0 błędów. |
| Buildy aplikacji i APK testów DEV/PROD | Sukces. |
| Projektowy `qualityCheck` | Sukces: ktlint, standardowy detekt i lint. |
| Dodatkowy `lintProdDebug` | Sukces. Lint obu wariantów: 0 błędów, po 25 ostrzeżeń konfiguracji i zasobów. |
| `detektDevDebug`, `detektProdDebug` z informacją o typach | Nie przechodzą: po 4 wcześniejsze uwagi poza nowym kodem. |
| Ochrona logiki i danych | 43 chronione pliki mają te same SHA-256 co przed zmianami. |
| Wygląd Playground po cleanupie | Wszystkie 12 PNG identyczne bajt w bajt z kopią sprzed migracji. |
| Zawartość obu APK | Silnik Canvas obecny; brak starego zasobu i klas odtwarzania/resolvera. |
| Dostępność Playground w APK | Klasa ekranu obecna w DEV, nieobecna w PROD. |

Pełne zestawy testów Android obejmują również 14 testów repozytorium Room
w każdym wariancie. Uruchomiono je przez AndroidJUnitRunner na `emulator-5554`,
aby nie instalować aplikacji ani nie wykonywać testów na podłączonym telefonie.
Uruchomiono też rzeczywiste `MainActivity` obu aplikacji i obejrzano Home
po zakończeniu przejść nawigacji. Potwierdzono przycisk Playground w DEV
i jego brak w PROD.

Pozostałe uwagi analizy typów:

- `MainActivity.kt`: nieużywana funkcja `AppPreview`.
- `HomeScreen.kt`: nieużywana funkcja `HomeScreenPreview` (uwaga obecna przed cleanupem).
- `HabitHistoryDao.kt`: liczba parametrów istniejącej metody optymalizacji historii.
- `RepositoryModule.kt`: sugestia zastąpienia klasy abstrakcyjnej interfejsem.

Nie zmieniano tych elementów w celu wyciszenia istniejących uwag.
Ostrzeżenia lint dotyczą docelowego SDK, etykiety Activity, dostępności nowszych
wersji zależności oraz istniejących nieużywanych kolorów. Nie aktualizowano
zależności ani nie usuwano niezwiązanych z Jive zasobów.

Artefakty lokalne, poza Git:

```text
app/build/outputs/jive-cleanup/pre-removal/dev-tests.txt
app/build/outputs/jive-cleanup/pre-removal/prod-tests.txt
app/build/outputs/jive-cleanup/final/dev-tests.txt
app/build/outputs/jive-cleanup/final/prod-tests.txt
app/build/outputs/jive-cleanup/final/playground-comparison.json
app/build/outputs/jive-cleanup/final/home-app-dev.png
app/build/outputs/jive-cleanup/final/home-app-prod.png
```

## Pozostałości starego systemu

Nie ma aktywnych odwołań do usuniętych klas, zasobu ani generatorów w źródłach,
konfiguracji i CI. Stare nazwy występują wyłącznie w historycznej liście usunięć
w tym raporcie oraz w historii Git. Sprawdzono też deskryptory klas i wpisy
zasobów bezpośrednio w finalnych APK. Pozostałe 10 WebP to ikony aplikacji.

## Elementy zachowane i ograniczenia

Zachowano modele kondycji, scoring i jego progi, historię i jej optymalizację,
kontroler, ViewModel, moduły DI, encje, DAO, repozytoria, mappery, schemat Room
oraz 10 ikon WebP. Nie zmieniono wersji bazy, nazwy bazy ani jej konfiguracji.
Nie usuwano współdzielonego AndroidX Core ani innych zależności aplikacji.

Home celowo nie pokazuje jeszcze różnic mood, energy, sleepiness i reaction.
Ruch i garderoba pozostają prototypem frontalnym, bez nowych animacji ani IK.
Nie przeprowadzono pomiaru gwarantującego 60 Hz na fizycznych urządzeniach.

## Checklista manualna

- Uruchomić DEV i PROD; potwierdzić wyśrodkowanego Jive Canvas na Home,
  strój Default, oddech i mruganie oraz brak przycięcia postaci.
- Sprawdzić Home na małym ekranie, po obrocie i przy większej skali tekstu.
- Przenieść aplikację do tła i wznowić; sprawdzić zatrzymywanie i płynny powrót.
- W DEV otworzyć Playground, sprawdzić wszystkie cztery animacje i trzy stroje,
  zmieniać ubranie podczas chodu i sprawdzić automatyczny powrót Happy do Idle.
- Wrócić z Playground przyciskiem i systemowym gestem Wstecz.
- Potwierdzić brak wejścia do Playground w PROD.
- Sprawdzić zachowanie po zmianach nawyków: dane i kondycja nadal się obliczają,
  a wygląd Home pozostaje świadomie Idle / Default.
