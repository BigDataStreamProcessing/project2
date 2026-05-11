# Generator Parkingowy — Opis Modułu

## 1. Opis danych źródłowych

### 1.1 Strumień

Pojedynczy rekord w strumieniu reprezentuje zdarzenie zarejestrowane przez system zarządzania parkingiem — wjazd pojazdu, wyjazd lub aktualizację stanu miejsca.

| Pole | Typ | Opis dziedzinowy |
|---|---|---|
| `eventId` | `String` | Unikalny identyfikator zdarzenia |
| `spotId` | `String` | Identyfikator konkretnego miejsca parkingowego (np. `Z-A-042`) |
| `zoneId` | `String` | Identyfikator strefy parkingowej; klucz łączący ze słownikiem |
| `timestamp` | `Instant` | Czas zarejestrowania zdarzenia przez system |
| `eventType` | `String` | Typ zdarzenia: `ENTRY` — wjazd, `EXIT` — wyjazd, `STATUS_UPDATE` — aktualizacja stanu miejsca |
| `vehicleHash` | `String` | Zanonimizowany hash tablicy rejestracyjnej pojazdu |
| `vehicleCategory` | `String` | Kategoria pojazdu: `CAR`, `TRUCK`, `MOTORCYCLE`, `EV` |
| `entryTime` | `Instant` | Czas wjazdu pojazdu na parking |
| `durationMinutes` | `Integer` | Czas postoju w minutach; wypełniony wyłącznie dla zdarzeń `EXIT` |
| `paymentMethod` | `String` | Metoda płatności: `APP`, `METER`, `CARD`, `SUBSCRIPTION` |
| `spotOccupied` | `boolean` | Stan miejsca po zdarzeniu: zajęte (`true`) lub wolne (`false`) |
| `evChargingPoint` | `boolean` | Czy pojazd korzysta z punktu ładowania (`true` dla kategorii `EV`) |
| `evCharging` | `boolean` | Czy trwa aktywne ładowanie pojazdu elektrycznego |
| `batteryLevelAtEntry` | `double` | Poziom naładowania baterii przy wjeździe [%]; `-1` dla pojazdów spalinowych |
| `sensorConfidence` | `double` | Pewność odczytu czujnika indukcyjnego w przedziale 0–1 |
| `distanceToCenterM` | `double` | Przybliżona odległość miejsca parkingowego od centrum miasta [m]; losowana z przedziału właściwego dla strefy |

Pole `durationMinutes` jest wypełnione wyłącznie przy zdarzeniach `EXIT` — to jedyne zdarzenia niosące informację o zakończonym pobycie pojazdu. Opłata za postój oraz ocena, czy postój przekroczył dozwolony limit, nie są częścią surowego zdarzenia — wyliczane są podczas przetwarzania strumienia po wzbogaceniu o dane ze słownika.

### 1.2 Słownik

| Pole | Typ | Opis |
|---|---|---|
| `zoneId` | `String` | **Klucz łączący ze strumieniem** |
| `name` | `String` | Nazwa strefy (np. Strefa A – Centrum) |
| `capacity` | `int` | Liczba miejsc parkingowych w strefie |
| `pricePerHourPLN` | `double` | Stawka godzinowa za postój [PLN] |
| `maxStayHours` | `int` | Maksymalny dozwolony czas postoju [h] |
| `type` | `String` | Charakter strefy: `PAID` — płatna, `P+R` — Park and Ride, `HOSPITAL` — szpitalna |
| `district` | `String` | Dzielnica miasta |
| `distanceToCenterMinM` | `double` | Minimalna odległość od centrum dla miejsc w tej strefie [m] |
| `distanceToCenterMaxM` | `double` | Maksymalna odległość od centrum dla miejsc w tej strefie [m] |

Połączenie strumienia ze słownikiem przez `zoneId` osadza każde zdarzenie w kontekście miejskim. Bez słownika wiemy, że pojazd opuścił strefę `Z-A` po 47 minutach — nie wiemy jednak, że stawka wynosi 6 PLN/h, a maksymalny czas postoju to 2 godziny. Dopiero ta wiedza pozwala wyliczyć należną opłatę, ocenić czy postój był zgodny z regulaminem oraz porównywać aktywność między strefami płatnymi a Park and Ride.

---

## 2. Zadanie projektowe

Systemy zarządzania parkingami miejskimi generują ciągły strumień zdarzeń ze wszystkich stref i miejsc. Celem analizy jest monitorowanie w czasie rzeczywistym aktywności poszczególnych stref, wykrywanie pojazdów naruszających regulamin postoju oraz budowanie dziennego obrazu tego, jak kierowcy korzystają ze stref różnego typu — jak długo parkują, jak często i jak rozkłada się czas postoju przez cały dzień.

### Parametry sterujące generatorem

| Parametr | Wartość domyślna | Dziedzinowa interpretacja |
|---|---|---|
| `generator.interval.ms` | `3000` | Jedno nowe zdarzenie co 3 sekundy — odpowiada aktywności systemu rejestracji przy umiarkowanym natężeniu ruchu |
| `generator.anomaly.probability` | `0.05` | 5 na każde 100 zdarzeń pochodzi od pojazdu, którego czas wjazdu wykracza poza dozwolony limit strefy — przy wyjezdzie `durationMinutes` będzie ponadnormatywnie wysokie |
| `generator.disorder.max.ms` | `30000` | Maksymalne rozproszenie czasowe zdarzeń w temacie Kafka — dane wysyłane są natychmiast, lecz wskutek warunków sieciowych mogą docierać w kolejności innej niż chronologiczna, z odchyleniem do 30 sekund względem czasu pomiaru |

### Poziom 1 — aktywność per strefa (okno 1-minutowe)

Analiza prowadzona jest w rozłącznych oknach jednominutowych, osobno dla każdej strefy. Każde okno opisuje, co działo się w tej strefie przez tę minutę: ile pojazdów wjechało, ile wyjechało i jak długo trwały zakończone postoje. Miary są proste i niewymagające stanu między oknami.

**Miara 1 — Liczba przyjazdów**
Liczba zdarzeń `ENTRY` w oknie minutowym dla danej strefy. Bezpośrednia miara aktywności wjazdowej — pokazuje, jak intensywnie strefa jest odwiedzana. Zachowana jako wartość bezwzględna umożliwiająca sumowanie na poziomie dziennym.

**Miara 2 — Średni czas postoju [min]**
Średnia wartości `durationMinutes` wyłącznie dla zdarzeń `EXIT` w oknie minutowym. Tylko zdarzenia `EXIT` niosą informację o zakończonym pobycie — pozostałe typy zdarzeń nie mają czasu postoju i nie są uwzględniane. Miara opisuje, jak długo trwały postoje zakończone w tej minucie.

**Miara 3 — Liczba wyjazdów**
Liczba zdarzeń `EXIT` w oknie minutowym dla danej strefy. W zestawieniu z Miarą 1 daje obraz rotacji — strefa z wieloma przyjazdami i małą liczbą wyjazdów sygnalizuje narastające obłożenie. Zachowana jako wartość bezwzględna umożliwiająca obliczenie ważonego średniego czasu postoju na poziomie dziennym.

> Wyniki tego poziomu powinny być możliwe do dalszego przetwarzania oraz do obserwacji za pomocą narzędzi zewnętrznych (najnowsze dane dla każdej strefy).

### Wzbogacenie kontekstem słownikowym

Minutowe wyniki per strefa są łączone ze słownikiem przez `zoneId`. Wzbogacenie dostarcza pola `type` — klucza grupowania na poziomie dziennym. Pole `pricePerHourPLN` umożliwia wyliczenie należnej opłaty za postoje zakończone w danym oknie. Pole `maxStayHours` pozwala oznaczyć zdarzenia `EXIT`, w których `durationMinutes` przekracza dozwolony limit — co jest podstawą wykrywania naruszeń regulaminu bez osobnego pola alertowego w strumieniu.

### Poziom 2 — dzienny raport per typ strefy (narastająco w ciągu doby)

Analiza dzienna budowana jest wyłącznie na podstawie wzbogaconych wyników minutowych — bez powrotu do surowego strumienia zdarzeń. Grupowanie po polu `type`. Każdy typ strefy dostarcza w ciągu doby tysiące obserwacji minutowych ze wszystkich należących do niego stref, co zapewnia wystarczającą masę danych dla analizy rozkładu. Wyniki aktualizowane są przy każdym napływającym wyniku minutowym.

**Miara 1 — Łączna liczba przyjazdów per typ strefy**
Narastająca suma przyjazdów ze wszystkich minut i stref danego typu w bieżącej dobie. Wyliczalna wprost z wyników minutowych. Pozwala porównywać, które typy stref przyciągają największy ruch i jak zmienia się ten ruch w ciągu dnia.

**Miara 2 — Średni czas postoju per typ strefy**
Ważona średnia czasów postoju ze wszystkich minut i stref danego typu — obliczana jako iloraz sumy iloczynów średniego czasu i liczby wyjazdów z każdego okna minutowego przez łączną liczbę wyjazdów. Wyliczalna wprost z Miary 2 i Miary 3 wyników minutowych. Pozwala porównywać, czy kierowcy parkują dłużej w centrum niż na Park and Ride.

**Miara 3 — Rozkład czasów postoju w strefach danego typu**
Chcemy wiedzieć, jaki czas postoju jest normą dla stref danego typu w ciągu doby — nie jak wypadła średnia, lecz gdzie skupia się większość parkujących. Kierowca zostawiający auto na 3 godziny w strefie P+R i ten, który parkuje przez 15 minut, wchodzą w tę samą średnią, ale opisują zupełnie różne zachowania. Pełny rozkład pozwala odpowiedzieć, jaki czas jest typowy — gdzie mieści się połowa wszystkich postojów — a jakie postoje należą do wyjątków. Kilka rażąco długich naruszeń regulaminu może przesunąć średnią w górę, nie zmieniając obrazu tego, jak parkuje większość kierowców. Przy rosnącej liczbie stref i obserwacji wyznaczenie tego rozkładu dokładnie staje się kosztowne i wymaga metod przybliżonych. Wyniki tego poziomu muszą być zapisywane w formie pozwalającej na ich łączenie z wynikami kolejnych dób bez powrotu do danych źródłowych.

Wyniki mają być dostępne na ujściu najszybciej jak to możliwe.

### Sygnał alarmowy — przekroczenie dozwolonego czasu postoju

**1. Czym jest zjawisko**
Każda strefa parkingowa objęta jest regulaminem określającym maksymalny czas postoju — od 2 godzin w centrum po 24 godziny na parkingu szpitalnym. Pojazd przekraczający ten limit blokuje miejsce innym użytkownikom i generuje nieuregulowane należności. W strefach o wysokiej rotacji nawet jeden pojazd przekraczający limit w godzinach szczytu może zablokować kilka potencjalnych płatnych postojów.

**2. Co obserwujemy w strumieniu**
Generator wydłuża `entryTime` pojazdów anomalnych o co najmniej 30 minut ponad `maxStayHours` strefy — w efekcie zdarzenie `EXIT` dla takiego pojazdu niesie `durationMinutes` wyraźnie przekraczające dozwolony limit. Naruszenia generowane są z prawdopodobieństwem 5% (`generator.anomaly.probability = 0.05`). Wykrycie naruszenia wymaga porównania `durationMinutes` z `maxStayHours × 60` po wzbogaceniu zdarzenia `EXIT` o dane słownikowe — bez słownika nie wiadomo, jaki limit obowiązuje w danej strefie.

**3. Kiedy bić na alarm**
Alarm gdy w dowolnych 5 minutach następujących bezpośrednio po sobie dla jednej strefy pojawią się co najmniej 3 zdarzenia `EXIT`, w których `durationMinutes > maxStayHours × 60` po wzbogaceniu o słownik — lub gdy dla dowolnego pojazdu `durationMinutes` przekroczy dwukrotność dozwolonego limitu, co wskazuje na rażące naruszenie regulaminu wymagające natychmiastowej interwencji.

Sygnały alarmowe powinny być dostępne najszybciej jak to tylko możliwe dla narzędzi zewnętrznych.

# Uwagi ogólne dotyczące implementacji

* Należy zwrócić uwagę na dokonywanie poprawnych obliczeń
* Należy uwzględnić fakt pojawiania się zdarzeń nieuporządkowanych
* Dane słownikowe należy dostarczyć w sposób, aby była możliwa ich zmiana w trakcie przetwarzania
* Aplikacja musi być zabezpieczona przed awariami, tak aby mogła je samodzielnie obsługiwać, lub jeśli to nie możliwe, wznowienie jej działania nie prowadziło do utraty danych. Poziom dostarczanych gwarancji *exactly-once*.
* Ujście musi być trwałą składnicą uwzględniającą wymagane gwarancje. 