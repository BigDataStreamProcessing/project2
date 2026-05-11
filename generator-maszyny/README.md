# Generator Maszyn Produkcyjnych — Opis Modułu

Moduł generuje syntetyczny strumień pomiarów pracy maszyn oraz udostępnia statyczny słownik maszyn. Dane publikowane są na temat Kafki i mogą służyć jako dane wejściowe dla przetwarzania strumieni danych.

## 1. Opis danych źródłowych

### 1.1 Strumień

Pojedynczy rekord w strumieniu reprezentuje jeden odczyt telemetryczny maszyny produkcyjnej — chwilowy obraz jej stanu mechanicznego, elektrycznego i wydajnościowego zarejestrowany przez system SCADA w trakcie zmiany roboczej.

| Pole | Typ | Opis dziedzinowy |
|---|---|---|
| `machineId` | `String` | Identyfikator maszyny; klucz łączący ze słownikiem |
| `timestamp` | `Instant` | Czas wykonania odczytu telemetrycznego |
| `machineState` | `String` | Bieżący stan maszyny: `RUNNING` — produkcja, `IDLE` — postój, `SETUP` — przezbrojenie, `FAULT` — awaria, `MAINTENANCE` — przegląd |
| `shift` | `String` | Zmiana robocza: `A` (6:00–14:00), `B` (14:00–22:00), `C` (22:00–6:00) |
| `spindleRpm` | `double` | Aktualna prędkość obrotowa wrzeciona [obr/min] |
| `spindleLoadPercent` | `double` | Obciążenie wrzeciona wyrażone jako procent wartości nominalnej |
| `feedRateMmMin` | `double` | Prędkość posuwu narzędzia [mm/min] |
| `motorCurrentA` | `double` | Pobierany prąd silnika [A] |
| `motorVoltageV` | `double` | Napięcie zasilania silnika [V] |
| `powerKw` | `double` | Chwilowy pobór mocy [kW] |
| `energyKwhDelta` | `double` | Zużycie energii elektrycznej w tym odczycie [kWh] |
| `bearingTempC` | `double` | Temperatura łożysk [°C] |
| `motorTempC` | `double` | Temperatura silnika [°C] |
| `coolantTempC` | `double` | Temperatura cieczy chłodzącej [°C] |
| `vibrationXMms` | `double` | Drgania w osi X [mm/s] |
| `vibrationYMms` | `double` | Drgania w osi Y [mm/s] |
| `vibrationZMms` | `double` | Drgania w osi Z [mm/s] |
| `vibrationRmsMms` | `double` | Skuteczna wartość drgań ze wszystkich osi [mm/s] |
| `partsProducedDelta` | `int` | Liczba sztuk wyprodukowanych w tym odczycie |
| `defectPartsDelta` | `int` | Liczba sztuk wadliwych wykrytych w tym odczycie |
| `cycleTimeS` | `double` | Rzeczywisty czas cyklu produkcyjnego [s]; zero gdy maszyna nie produkuje |

Flagi alarmowe (przekroczenie progów drgań i temperatury) nie są częścią surowego odczytu — wyliczane są podczas przetwarzania strumienia po wzbogaceniu o dane ze słownika.

### 1.2 Słownik

| Pole | Typ | Opis |
|---|---|---|
| `machineId` | `String` | **Klucz łączący ze strumieniem** |
| `name` | `String` | Nazwa handlowa maszyny (np. Centrum obróbcze CNC) |
| `type` | `String` | Typ technologiczny (np. `CNC_MILL`, `WELDING_ROBOT`, `HYDRAULIC_PRESS`) |
| `nominalRpm` | `double` | Nominalna prędkość obrotowa wrzeciona [obr/min] |
| `nominalCurrentA` | `double` | Nominalny pobór prądu przy pełnym obciążeniu [A] |
| `vibrationWarnMms` | `double` | Próg drgań ostrzegawczych wynikający ze specyfikacji producenta [mm/s] |
| `vibrationAlarmMms` | `double` | Próg drgań alarmowych wynikający ze specyfikacji producenta [mm/s] |
| `tempWarnC` | `double` | Próg temperatury ostrzegawczej łożysk [°C] |
| `tempAlarmC` | `double` | Próg temperatury alarmowej łożysk [°C] |
| `productionLine` | `String` | Linia produkcyjna, do której należy maszyna (`L1`, `L2`, `L3`) |
| `plannedMaintenanceDays` | `int` | Interwał planowych przeglądów wyrażony w dniach |

Połączenie odczytów telemetrycznych ze słownikiem przez `machineId` nadaje surowym pomiarom kontekst technologiczny i organizacyjny. Bez słownika wiemy, że maszyna `ROB-001` rejestruje drgania na poziomie 4,2 mm/s — nie wiemy jednak, że to robot spawalniczy, dla którego próg alarmowy wynosi 5,0 mm/s, a więc maszyna pracuje w 84% swojego limitu. Dopiero ta wiedza pozwala ocenić, czy odczyt jest niepokojący, przypisać maszynę do linii produkcyjnej i planować przeglądy w kontekście harmonogramu produkcji.

---

## 2. Zadanie projektowe

Hale produkcyjne generują ciągły strumień odczytów telemetrycznych ze wszystkich maszyn. Celem analizy jest śledzenie w czasie rzeczywistym efektywności produkcyjnej i energetycznej każdej maszyny, wykrywanie stanów zagrożenia wymagających natychmiastowej interwencji oraz budowanie obrazu kondycji poszczególnych linii produkcyjnych w podziale na zmiany robocze.

### Parametry sterujące generatorem

| Parametr | Wartość domyślna | Dziedzinowa interpretacja |
|---|---|---|
| `generator.interval.ms` | `4000` | Jeden nowy odczyt telemetryczny co 4 sekundy — generator losuje maszynę spośród wszystkich aktywnych, więc częstotliwość odczytów dla konkretnej maszyny jest zmienna |
| `generator.anomaly.probability` | `0.06` | 6 na każde 100 odczytów niesie cechy charakterystyczne dla stanu awaryjnego — ponadnormatywne drgania, przegrzanie łożysk i wzrost poboru prądu |
| `generator.disorder.max.ms` | `30000` | Maksymalne rozproszenie czasowe zdarzeń w temacie Kafka — dane wysyłane są natychmiast, lecz wskutek warunków sieciowych mogą docierać w kolejności innej niż chronologiczna, z odchyleniem do 30 sekund względem czasu pomiaru |

### Poziom 1 — aktywność per maszyna (okno 1-minutowe)

Analiza prowadzona jest w rozłącznych oknach jednominutowych, osobno dla każdej maszyny. Ponieważ generator losuje maszynę przy każdym odczycie spośród wszystkich aktywnych maszyn, liczba próbek przypadających na konkretną maszynę w oknie minutowym jest zmienna — miary mają charakter diagnostyczny, a nie precyzyjny pomiar.

**Miara 1 — Liczba dobrych sztuk w minucie**
Suma pola `partsProducedDelta` pomniejszona o sumę pola `defectPartsDelta` dla danej maszyny w oknie minutowym. Każdy odczyt wnosi do sumy tylko tyle sztuk, ile zostało wyprodukowanych od poprzedniego odczytu tej maszyny — bez potrzeby śledzenia stanu między oknami. Zachowana jako wartość bezwzględna umożliwiająca sumowanie na poziomie linii.

**Miara 2 — Zużycie energii w minucie [kWh]**
Suma pola `energyKwhDelta` dla danej maszyny w oknie minutowym. Analogicznie do produkcji — każdy odczyt wnosi przyrost energii zarejestrowany w danej chwili. Wartość bezwzględna umożliwiająca sumowanie. W zestawieniu z Miarą 1 pozwala obliczyć energochłonność produkcji: ile energii kosztuje wytworzenie jednej dobrej sztuki.

**Miara 3 — Średni poziom drgań w minucie [mm/s]**
Średnia wartości `vibrationRmsMms` ze wszystkich odczytów danej maszyny w oknie minutowym. Ze względu na zmienną liczbę próbek miara ma charakter orientacyjny — jej wartość analityczna ujawnia się dopiero w przekroju wielu minut i maszyn na poziomie linii.

> Wyniki tego poziomu powinny być możliwe do dalszego przetwarzania oraz do obserwacji za pomocą narzędzi zewnętrznych (najnowsze dane dla każdej maszyny).

### Wzbogacenie kontekstem słownikowym

Wyniki minutowe per maszyna są łączone ze słownikiem przez `machineId`. Wzbogacenie dostarcza trzech grup informacji. Pole `productionLine` jest kluczem grupowania na poziomie linii — pozwala przypisać wyniki każdej maszyny do jej linii i zmiany. Progi `vibrationAlarmMms` i `tempAlarmC` umożliwiają wyznaczenie, czy dana minuta przekroczyła wartości krytyczne. Pole `plannedMaintenanceDays` w zestawieniu z historią alarmów pozwala ocenić, jak blisko granicy serwisowej pracuje dana maszyna.

Wykrywanie anomalii może działać również na danych elementarnych — bezpośrednio na surowym strumieniu wzbogaconym o słownik, przed agregacją minutową — co pozwala reagować na pojedyncze przekroczenia progów bez oczekiwania na zamknięcie okna.

### Poziom 2 — raport per linia produkcyjna i zmianę (narastająco w ciągu doby)

Analiza dzienna budowana jest wyłącznie na podstawie wzbogaconych wyników minutowych — bez powrotu do surowego strumienia odczytów. Grupowanie po kombinacji linii produkcyjnej (`productionLine`) i zmiany roboczej (`shift`). Każda linia dostarcza w ciągu jednej zmiany kilkaset obserwacji minutowych z wszystkich swoich maszyn, co zapewnia wystarczającą masę danych dla analizy rozkładu. Wyniki aktualizowane są przy każdym napływającym wyniku minutowym.

**Miara 1 — Łączna produkcja dobra per linia i zmianę**
Narastająca suma dobrych sztuk ze wszystkich minut i maszyn danej linii w bieżącej zmianie. Wyliczalna wprost z wyników minutowych. Pozwala porównywać wydajność linii między zmianami i śledzić jej trend w kolejnych dniach.

**Miara 2 — Łączne zużycie energii per linia i zmianę**
Narastająca suma zużycia energii ze wszystkich minut i maszyn linii w bieżącej zmianie. Wyliczalna wprost z wyników minutowych. W zestawieniu z Miarą 1 pozwala na bieżąco śledzić energochłonność produkcji na linii i porównywać ją między zmianami oraz liniami.

**Miara 3 — Typowy poziom drgań maszyn linii**
Mediana średnich poziomów drgań ze wszystkich minut i maszyn obserwowanych dla danej linii i zmiany. Chcemy wiedzieć, jaki poziom drgań był normą dla tej linii w tej zmianie — nie jak wypadły najgorsze minuty, lecz gdzie leży środek rozkładu obserwacji. Kilka minut z anomalnie wysokimi drganiami przesuwa średnią, nie ruszając mediany; systematycznie podwyższona mediana to sygnał postępującego zużycia, który nie jest jeszcze widoczny w pojedynczych odczytach alarmowych. Przy rosnącej liczbie maszyn i zmian wyznaczenie mediany dokładnie wymaga metod przybliżonych. Wyniki tego poziomu muszą być zapisywane w formie pozwalającej na ich łączenie z wynikami kolejnych dób bez powrotu do danych źródłowych.

Wyniki mają być dostępne na ujściu najszybciej jak to możliwe.

### Sygnał alarmowy — przeciążenie termiczne i wibracyjne

**1. Czym jest zjawisko**
Jednoczesny wzrost drgań i temperatury łożysk to klasyczny wzorzec ostrego uszkodzenia — luzu łożyskowego, pęknięcia elementu tocznego lub początku zatarcia. Każde z tych zjawisk osobno może być przejściowe; ich współwystąpienie w jednym odczycie wskazuje na proces, który bez interwencji prowadzi do nieplanowanego przestoju. W środowisku produkcyjnym nieplanowany przestój jest wielokrotnie droższy od planowego przeglądu.

**2. Co obserwujemy w strumieniu**
W stanie awaryjnym generator podnosi drgania do 1,5-krotności progu ostrzegawczego powiększonego o losowy naddatek — dla centrum CNC próg ostrzegawczy wynosi 4,5 mm/s, więc wartość anomalna przekracza 6,75 mm/s przy progu alarmowym 8,0 mm/s. Równocześnie temperatura łożysk wzrasta o 20°C ponad normalny zakres pracy, a pobór prądu osiąga 135% wartości nominalnej. Anomalie generowane są z prawdopodobieństwem 6% (`generator.anomaly.probability = 0.06`).

**3. Kiedy bić na alarm**
Alarm gdy w dowolnych 5 minutach następujących bezpośrednio po sobie dla jednej maszyny pojawią się co najmniej 3 odczyty z poziomem drgań przekraczającym próg alarmowy (`vibrationRmsMms > vibrationAlarmMms`) — lub gdy dla dowolnej maszyny wystąpi choćby jeden odczyt z jednoczesnym przekroczeniem progu alarmowego drgań i progu alarmowego temperatury łożysk (`bearingTempC > tempAlarmC`). Oba progi porównywane są po wzbogaceniu surowego strumienia o dane ze słownika. Warunek pierwszy wychwytuje narastające zużycie mechaniczne widoczne w serii podwyższonych odczytów. Warunek drugi reaguje natychmiast — współwystąpienie obu przekroczeń w jednym odczycie to sygnał ostrego uszkodzenia niewymagający potwierdzenia przez kolejne próbki.

Sygnały alarmowe powinny być dostępne najszybciej jak to tylko możliwe dla narzędzi zewnętrznych.

# Uwagi ogólne dotyczące implementacji

* Należy zwrócić uwagę na dokonywanie poprawnych obliczeń
* Należy uwzględnić fakt pojawiania się zdarzeń nieuporządkowanych
* Dane słownikowe należy dostarczyć w sposób, aby była możliwa ich zmiana w trakcie przetwarzania
* Aplikacja musi być zabezpieczona przed awariami, tak aby mogła je samodzielnie obsługiwać, lub jeśli to nie możliwe, wznowienie jej działania nie prowadziło do utraty danych. Poziom dostarczanych gwarancji *exactly-once*.
* Ujście musi być trwałą składnicą uwzględniającą wymagane gwarancje oraz umożliwiającą dokonywanie analiz zebranych tam danych (np. agregacji na wyższych poziomach). 