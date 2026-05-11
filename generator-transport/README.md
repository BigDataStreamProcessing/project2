# Generator Danych Transportu Miejskiego — Opis Modułu

Generator strumienia odczytów pozycji GPS pojazdów komunikacji miejskiej publikowanych do tematu Kafka `transport-pozycje`.

---

## 1. Dane źródłowe

### 1.1 Strumień — `transport-pozycje`

Pojedynczy rekord reprezentuje automatyczny odczyt pozycji GPS pojazdu komunikacji miejskiej: chwilowy stan pojazdu obejmujący jego lokalizację, prędkość, liczbę pasażerów oraz odchylenie od rozkładu jazdy.

| Pole | Typ | Opis dziedzinowy |
|---|---|---|
| `vehicleId` | String | Identyfikator pojazdu (autobusu, tramwaju lub składu metra) w flocie przewoźnika |
| `lineId` | String | Identyfikator linii komunikacyjnej, na której pojazd aktualnie kursuje; klucz do słownika tras |
| `currentStop` | String | Nazwa przystanku, przy którym pojazd aktualnie się znajduje lub który właśnie minął |
| `nextStop` | String | Nazwa kolejnego przystanku na trasie |
| `timestamp` | Instant | Czas odczytu GPS (UTC) |
| `latitude` | double | Szerokość geograficzna pozycji pojazdu [°] |
| `longitude` | double | Długość geograficzna pozycji pojazdu [°] |
| `speedKmh` | double | Chwilowa prędkość pojazdu [km/h]; wartość 0 oznacza postój |
| `headingDeg` | double | Kierunek jazdy w stopniach: 0° = północ, 90° = wschód |
| `passengerCount` | int | Liczba pasażerów na pokładzie w chwili odczytu |
| `delaySeconds` | int | Odchylenie od rozkładu jazdy w sekundach; wartości ujemne oznaczają przyjazd przed czasem, dodatnie — opóźnienie |
| `doorsOpen` | boolean | Czy drzwi pojazdu są otwarte — wyłącznie podczas postoju na przystanku |
| `airConditioningOn` | boolean | Czy klimatyzacja jest włączona |
| `engineTempC` | double | Temperatura silnika [°C] |
| `fuelLevelPercent` | double | Poziom paliwa lub naładowania baterii (dla pojazdów elektrycznych) [%] |


### 1.2 Słownik — `routes.json`

| Pole | Typ | Opis |
|---|---|---|
| **`lineId`** | String | Identyfikator linii — klucz łączący ze strumieniem |
| `name` | String | Nazwa trasy (np. „Centrum – Lotnisko") |
| `type` | String | Typ środka transportu: `BUS` — autobus (pojemność 80 os.), `TRAM` — tramwaj (200 os.), `METRO` — metro (800 os.) |
| `stops` | List\<String\> | Uporządkowana lista przystanków na trasie |
| `scheduledIntervalMin` | int | Rozkładowy interwał kursowania [min] — czas między kolejnymi kursami tej samej linii |
| `normalSpeedKmh` | double | Typowa prędkość przejazdu w warunkach bez zakłóceń [km/h] |

Słownik pozwala zinterpretować odczyt w kontekście operacyjnym trasy. Znając wyłącznie strumień wiemy, że pojazd na linii L001 ma opóźnienie 340 sekund — nie wiemy jednak, że to autobus z rozkładowym interwałem 10 minut, co oznacza, że spóźnienie pochłonęło już jedną trzecią czasu do następnego kursu i pasażerowie na kolejnych przystankach de facto czekają na dwa autobusy jednocześnie. Słownik dostarcza również nominalnej pojemności niezbędnej do wyznaczenia obłożenia pojazdu.

---

## 2. Zadanie projektowe

### Parametry sterujące

| Parametr | Wartość domyślna | Dziedzinowa interpretacja |
|---|---|---|
| `generator.interval.ms` | 5 000 | Odstęp między kolejnymi odczytami GPS — każdy pojazd emituje ping co 5 sekund |
| `generator.vehicles.count` | 30 | Liczba pojazdów aktywnych w symulacji |
| `generator.anomaly.probability` | 0,06 | Prawdopodobieństwo, że dany odczyt pochodzi z epizodu awarii — statystycznie co 17. ping |
| `generator.disorder.max.ms` | 30 000 | Maksymalne rozproszenie czasowe zdarzeń w temacie Kafka — dane wysyłane są natychmiast, lecz wskutek warunków sieciowych mogą docierać w kolejności innej niż chronologiczna, z odchyleniem do 30 sekund względem czasu pomiaru |

### Poziom 1 — stan per linia i pojazd (okno 1-minutowe)

Analiza prowadzona jest w rozłącznych oknach jednominutowych, osobno dla każdej kombinacji linii komunikacyjnej i pojazdu. Każde okno opisuje, w jakim stanie był dany pojazd na danej linii przez tę minutę: jak bardzo się spóźniał, z jaką prędkością się poruszał, ilu pasażerów wiózł i czy wystąpiły symptomy awarii. Miary są proste i niewymagające stanu między oknami.

**Miara 1 — Średnie opóźnienie**

Średnia wartości `delaySeconds` ze wszystkich odczytów danego pojazdu na danej linii w oknie minutowym. Opóźnienie jest podstawowym wskaźnikiem punktualności — miara opisuje, jak bardzo pojazd odbiegał od rozkładu przez daną minutę. Wartość ujemna oznacza jazdę przed rozkładem. Zachowana jako wartość bezwzględna pozwalająca na wyznaczenie dobowej średniej opóźnień per typ środka transportu.

**Miara 2 — Średnia prędkość**

Średnia wartości `speedKmh` w oknie minutowym. Prędkość w zestawieniu z `normalSpeedKmh` ze słownika pozwala na wyższym poziomie analizy ocenić, w jakiej części trasy pojazd poruszał się swobodnie, a gdzie napotykał utrudnienia. Odczyty z prędkością zerową świadczą o postoju — planowym na przystanku lub nieplanowanym wskutek awarii lub korka.

**Miara 3 — Średnia liczba pasażerów**

Średnia wartości `passengerCount` w oknie minutowym. Reprezentuje typowe obłożenie pojazdu w danej minucie kursu. Na wyższym poziomie analizy, po wzbogaceniu o pojemność nominalną wynikającą z typu linii, pozwala ocenić, przy których typach środków transportu obłożenie jest najwyższe i w jakich warunkach operacyjnych.

**Miara 4 — Liczba odczytów z unieruchomionym pojazdem i znaczącym opóźnieniem**

Liczba odczytów w oknie minutowym, w których jednocześnie `speedKmh = 0` i `delaySeconds > 300`. Surowy licznik symptomów awarii — pojazd stoi w miejscu i jest już ponad 5 minut za rozkładem. Zachowany jako wartość bezwzględna; udział takich okien per typ środka transportu wyliczany jest na wyższym poziomie analizy.

> Wyniki tego poziomu powinny być możliwe do dalszego przetwarzania oraz do obserwacji za pomocą narzędzi zewnętrznych (najnowsze dane dla każdej linii i pojazdu).

### Wzbogacenie kontekstem słownikowym

Minutowe wyniki per linia i pojazd łączone są ze słownikiem tras przez `lineId`. Wzbogacenie dostarcza pola `type` — klucza grupowania na poziomie dobowym — oraz `scheduledIntervalMin` i `normalSpeedKmh`, które pozwalają ocenić zmierzone opóźnienia i prędkości w kontekście założeń rozkładowych danej linii. Na tym etapie wyznaczane jest również `occupancyPercent` jako iloraz średniej liczby pasażerów i nominalnej pojemności pojazdu wynikającej z jego typu.

### Poziom 2 — dzienny raport per typ środka transportu (narastająco w ciągu doby)

Analiza dzienna budowana jest wyłącznie na podstawie wzbogaconych wyników minutowych — bez powrotu do surowego strumienia odczytów. Grupowanie po polu `type`. Każdy typ środka transportu dostarcza w ciągu doby tysięcy obserwacji minutowych ze wszystkich linii i pojazdów danego rodzaju. Wyniki aktualizowane są przy każdym napływającym wyniku minutowym.

**Miara 1 — Średnie dobowe opóźnienie per typ**

Narastająca średnia opóźnień ze wszystkich minut, linii i pojazdów danego typu w bieżącej dobie. Pozwala porównywać niezawodność rozkładową różnych środków transportu: czy metro jest statystycznie bardziej punktualne od autobusów, i jak ta różnica zmienia się w ciągu dnia roboczego względem weekendu.

**Miara 2 — Odsetek okien minutowych z symptomami awarii**

Udział okien minutowych, w których zarejestrowano co najmniej jeden odczyt z unieruchomionym pojazdem i opóźnieniem przekraczającym 5 minut, wśród wszystkich okien minutowych danego typu w bieżącej dobie. Odpowiada na pytanie, jak często i jak długo poszczególne typy środków transportu doświadczają poważnych zakłóceń — nie tylko jak bardzo się spóźniają.

**Miara 3 — Liczba unikalnych pojazdów obsługujących linie danego typu w ciągu doby**

Chcemy wiedzieć, ile różnych pojazdów faktycznie obsługiwało linie autobusowe, tramwajowe i metro w ciągu doby — czy flota jest w pełni wykorzystana, czy część pojazdów nie generuje żadnych odczytów. Przy kilkudziesięciu pojazdach odpowiedź jest prosta, ale floty miejskie liczą setki i tysiące jednostek taboru, a ta sama logika musi działać bez zmian przy dowolnej skali. Ze względu na fakt, że przy dużej flocie dokładne zliczanie unikalnych identyfikatorów pojazdów wymaga proporcjonalnie rosnących zasobów, dopuszczalne jest stosowanie metod przybliżonych, które dają odpowiedź z kontrolowanym błędem przy stałym zużyciu pamięci. Wyniki tego poziomu muszą być zapisywane w formie pozwalającej na ich łączenie z wynikami kolejnych dób bez powrotu do danych źródłowych.

> Wyniki tego poziomu mają być dostępne na ujściu najszybciej jak to możliwe.

### Sygnał alarmowy — pojazd unieruchomiony z pasażerami

Unieruchomienie pojazdu komunikacji miejskiej w trakcie kursu — poza planowym postojem na przystanku — to zdarzenie wymagające natychmiastowej reakcji dyspozytorskiej. Zablokowany pojazd wstrzymuje kurs, uniemożliwia wysiadkę i wsiadkę pasażerów na kolejnych przystankach oraz może prowadzić do kumulacji opóźnień na całej linii.

W strumieniu awaria manifestuje się prędkością zerową przy rosnącym opóźnieniu. Zdarzenia anomalne generowane są z prawdopodobieństwem 6% (`generator.anomaly.probability = 0.06`) i generują opóźnienia w przedziale 300–900 sekund przy prędkości 0 km/h. Po wzbogaceniu o typ linii wyznaczane jest `occupancyPercent` — procentowe obłożenie pojazdu — które decyduje o priorytecie alarmu: unieruchomiony, pełny autobus wymaga innej reakcji niż pusty skład metra poza godzinami szczytu.

Alarm powinien zostać wyzwolony w dwóch przypadkach. Licznikowy: gdy w dowolnych 5 minutach następujących bezpośrednio po sobie dla jednej linii pojawią się co najmniej 4 odczyty z prędkością zerową i opóźnieniem przekraczającym 300 sekund. Natychmiastowy: gdy dla dowolnego pojazdu wystąpi choćby jeden odczyt z opóźnieniem przekraczającym 600 sekund przy obłożeniu powyżej 80%.

> Sygnały alarmowe powinny być dostępne najszybciej jak to tylko możliwe dla narzędzi zewnętrznych.

# Uwagi ogólne dotyczące implementacji

* Należy zwrócić uwagę na dokonywanie poprawnych obliczeń
* Należy uwzględnić fakt pojawiania się zdarzeń nieuporządkowanych
* Dane słownikowe należy dostarczyć w sposób, aby była możliwa ich zmiana w trakcie przetwarzania
* Aplikacja musi być zabezpieczona przed awariami, tak aby mogła je samodzielnie obsługiwać, lub jeśli to nie możliwe, wznowienie jej działania nie prowadziło do utraty danych. Poziom dostarczanych gwarancji *exactly-once*.
* Ujście musi być trwałą składnicą uwzględniającą wymagane gwarancje oraz umożliwiającą dokonywanie analiz zebranych tam danych (np. agregacji na wyższych poziomach). 
