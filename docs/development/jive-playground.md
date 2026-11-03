# Jive 2.0 — wspólny renderer Canvas

## Uruchomienie

Home w DEV i PROD wyświetla tę samą postać Canvas w animacji **Idle** i stroju
**Default**. Zachowuje dotychczasowy kwadratowy kontener i wyśrodkowanie.
`JiveCondition` nadal przepływa z ViewModelu przez Activity i nawigację do Home,
ale nie jest jeszcze mapowany na animacje ani ubrania.

W `devDebug` naciśnij **Jive Playground** na dole Home, aby ręcznie zmieniać
animacje i stroje. Przycisk Wstecz oraz systemowy gest Wstecz wracają do Home.
Ekran demonstracyjny, jego zasoby i trasa należą do `src/dev`. PROD zawiera
silnik Canvas i Home, ale nie zawiera ekranu Playground ani wejścia do niego.
Nie ma dodatkowego Activity ani deep linku.

## Architektura

- `JivePose.kt`: wspólna przestrzeń 360 × 480, proporcje, stawy, jednolita skala
  i bufor parametrów pozy z interpolacją wszystkich kanałów.
- `JiveGeometry.kt`: ścieżki korpusu, kończyn, oczu, włosów i garderoby.
  Kończyny mają lokalny początek w stawie; lewa i prawa strona są osobno
  transformowane. Geometria jest zapamiętana na czas życia komponentu.
- `JiveAnimator.kt`: czyste obliczenia pozy oraz przerwalne przejścia 320 ms.
  Nowe przejście startuje od aktualnie wyświetlanej pozy. Bufory pozy są używane
  ponownie. Animator nie przyjmuje stroju ani dotychczasowego `JiveCondition`.
- `JiveCharacter.kt`: rysowanie Canvas. Kolejność: cień, nogi, ręce z rękawami,
  korpus, garderoba korpusu, włosy, opaska, oczy/źrenice i usta. Korpus oraz
  twarz mają wspólną transformację; każda kończyna obraca się we własnym stawie.
  Stopy w Idle są nieruchome, a oddychanie skaluje tułów względem jego podstawy.
- `JiveOutfit.kt`: strój jest uporządkowaną listą elementów przypiętych do stawów.
  Renderer rysuje ubranie wewnątrz tej samej transformacji co odpowiednią część
  ciała. Paleta postaci i strojów jest zdefiniowana w jednym miejscu.
- `JiveAnimationClock.kt`: wspólny zegar Compose Animation i obsługa cyklu życia,
  wydzielone z dotychczasowego Playground bez zmiany parametrów i zachowania.
- `JiveHomeCharacter.kt`: instancja animatora Idle / Default dla Home,
  dotychczasowe proporcje kontenera i odczyt sygnału klatki podczas rysowania.
- `JivePlaygroundScreen.kt`: chwilowy stan demonstracji i sterowanie.
  Garderoba nie jest kluczem zegara, więc jej zmiana nie
  restartuje animacji. Sygnał klatki jest odczytywany dopiero podczas rysowania,
  bez rekompozycji panelu sterowania przy każdej klatce.

`repeatOnLifecycle(RESUMED)` zatrzymuje zegar po opuszczeniu ekranu lub przejściu
aplikacji do tła. Po wznowieniu czas biegnie od zatrzymanej pozy. Usunięcie ekranu
anuluje korutynę. Wyłączone animacje systemowe dają statyczny podgląd. Nie ma
własnego wątku, `Thread.sleep`, dodatkowych bibliotek ani usług sieciowych.
Ścieżka ust jest aktualizowana w istniejącym obiekcie `Path`; pozostałe ścieżki
powstają raz. Margines przestrzeni projektowej obejmuje także szczyt podskoku.

## Animacje i stroje

| Animacja | Zachowanie |
| --- | --- |
| Idle | Oddech, subtelny ruch rąk, okresowe mruganie, nieruchome stopy. |
| Happy | Krótki podskok, uniesienie rąk, większy uśmiech; automatyczny powrót do Idle. Ponowne naciśnięcie powtarza reakcję. |
| Walking | Przeciwne fazy nóg i rąk, unoszenie stóp, delikatna praca tułowia; chód w miejscu. |
| Sad | Opuszczone ręce, pochylenie tułowia, spojrzenie w dół, smutne usta i wolniejszy oddech. |

Default nie ma warstw garderoby. Hoodie zawiera kremową bluzę z kapturem,
kieszenią i długimi rękawami. Sport zawiera koszulkę, spodenki i opaskę.
Nazwy kontrolek mają angielskie i polskie zasoby językowe.

## Dodanie stroju

1. Zdefiniuj `JiveOutfit` z listą `JiveGarment(joint, shape, color)` w `JiveWardrobe`.
2. Wybierz istniejące kształty albo dodaj nowy kształt do `JiveGarmentShape`, jego
   ścieżkę do `JiveGeometry` i obsługę rysowania w `JiveRenderer.garments`.
3. Przypnij element do `BODY`, `LEFT_ARM`, `RIGHT_ARM` lub `HEAD`.
   Kolejność listy ustala kolejność warstw na danym stawie.
4. Dodaj strój do `JiveWardrobe.All`, zasoby nazwy i mapowanie etykiety w ekranie.

Dodanie stroju nie wymaga zmiany `JiveMotion` ani `JiveAnimator`.
Na tym etapie model nie zawiera sklepu, waluty, zapisu stroju w bazie ani odblokowań.

## Pliki

Kod wspólny i testy silnika (ścieżki względem repozytorium):

```text
app/src/main/java/com/uniccomj/jivana/presentation/jiveplayground/JivePose.kt
app/src/main/java/com/uniccomj/jivana/presentation/jiveplayground/JiveAnimator.kt
app/src/main/java/com/uniccomj/jivana/presentation/jiveplayground/JiveGeometry.kt
app/src/main/java/com/uniccomj/jivana/presentation/jiveplayground/JiveCharacter.kt
app/src/main/java/com/uniccomj/jivana/presentation/jiveplayground/JiveOutfit.kt
app/src/main/java/com/uniccomj/jivana/presentation/jiveplayground/JiveAnimationClock.kt
app/src/main/java/com/uniccomj/jivana/presentation/components/JiveHomeCharacter.kt
app/src/test/java/com/uniccomj/jivana/presentation/jiveplayground/JiveAnimatorTest.kt
app/src/androidTest/java/com/uniccomj/jivana/presentation/home/JiveHomeScreenTest.kt
```

Ekran demonstracyjny i integracja wariantów:

```text
app/src/dev/java/com/uniccomj/jivana/presentation/jiveplayground/JivePlaygroundScreen.kt
app/src/dev/java/com/uniccomj/jivana/presentation/navigation/DevelopmentDestinations.kt
app/src/prod/java/com/uniccomj/jivana/presentation/navigation/DevelopmentDestinations.kt
app/src/dev/res/values-pl/strings.xml
app/src/androidTestDev/java/com/uniccomj/jivana/presentation/jiveplayground/JivePlaygroundScreenTest.kt
app/src/dev/res/values/strings.xml
app/src/androidTestProd/java/com/uniccomj/jivana/presentation/navigation/ProdNavigationTest.kt
```

## Weryfikacja

Testy jednostkowe sprawdzają oddech i mruganie, stałe położenie stóp w Idle,
przeciwne fazy chodu, automatyczny powrót z Happy, wszystkie pary przejść,
przerwanie przejścia, interpolację wszystkich kanałów, ciągłość i skończoność
wartości, skalowanie i odrzucanie nieprawidłowych kroków zegara.
Testy Compose wspólne dla DEV i PROD sprawdzają renderowanie i rozmiar Home,
niezależność wyglądu od kondycji oraz zatrzymywanie/wznawianie zegara.
Testy DEV sprawdzają wszystkie kombinacje animacji i strojów Playground,
zmianę garderoby podczas chodu, powrót z Happy i nawigację.
Test PROD sprawdza brak wejścia do Playground. Podglądy PNG są zapisywane
w katalogu zewnętrznym aplikacji na emulatorze.

Raport migracji, wyniki i checklistę opisuje [jive-cleanup.md](jive-cleanup.md).

Polecenia:

```powershell
.\gradlew.bat :app:testDevDebugUnitTest :app:testProdDebugUnitTest
.\gradlew.bat :app:assembleDevDebug :app:assembleProdDebug
.\gradlew.bat :app:assembleDevDebugAndroidTest :app:assembleProdDebugAndroidTest
.\gradlew.bat qualityCheck
.\gradlew.bat :app:detektDevDebug :app:detektProdDebug --continue
# Na wybranym emulatorze/urządzeniu:
.\gradlew.bat :app:connectedDevDebugAndroidTest
.\gradlew.bat :app:connectedProdDebugAndroidTest
```

## Ograniczenia

To postać frontalna, inspirowana załączoną kartą postaci, a nie pełny rig
z obrotem do widoku bocznego i tylnego. Ruchy kończyn są celowo proste, bez IK
i symulacji tkanin. Kondycja nawyków nie steruje jeszcze wyglądem Home.
Strój Playground zachowuje się po zmianie konfiguracji, ale ruch startuje
ponownie od Idle. Nie wykonano
pomiaru gwarantującego 60 Hz na fizycznych urządzeniach o różnej wydajności.
