# Generator Danych Sieci Wodociągowej — Opis Modułu

Generator strumienia odczytów czujników węzłów sieci wodociągowej publikowanych do tematu Kafka `wodociagi-odczyty`.

---

## 1. Dane źródłowe

### 1.1 Strumień — `wodociagi-odczyty`

Pojedynczy rekord reprezentuje automatyczny odczyt czujników zamontowanych w węźle sieci wodociągowej: chwilowe wartości ciśnienia, natężenia przepływu oraz parametrów jakości wody i stanu urządzeń mechanicznych.

| Pole | Typ | Opis dziedzinowy |
|---|---|---|
| `nodeId` | String | Identyfikator węzła sieci wodociągowej; klucz do słownika węzłów |
| `timestamp` | Instant | Czas wykonania odczytu (UTC) |
| `flowM3h` | double | Chwilowe natężenie przepływu wody przez węzeł [m³/h] |
| `pressureBar` | double | Ciśnienie wody w węźle [bar] |
| `pressureDropBarH` | double | Tendencja ciśnieniowa — zmiana ciśnienia ekstrapolowana na godzinę [bar/h]; wartości ujemne oznaczają spadek |
| `waterTempC` | double | Temperatura wody w rurociągu [°C] |
| `chlorineMgL` | double | Stężenie wolnego chloru — środka dezynfekującego utrzymującego bezpieczeństwo mikrobiologiczne wody [mg/L] |
| `turbidityNTU` | double | Mętność wody — miara przezroczystości; podwyższone wartości mogą wskazywać na zanieczyszczenie lub naruszenie przewodu [NTU] |
| `phLevel` | double | Odczyn pH wody; norma dla wody pitnej: 6,5–9,5 |
| `pumpSpeedRpm` | double | Prędkość obrotowa pompy [obr/min]; wartość 0 dla węzłów niebędących pompownią |
| `motorCurrentA` | double | Natężenie prądu silnika pompy [A]; wartość 0 dla węzłów niebędących pompownią |
| `vibrationMms` | double | Wibracje obudowy pompy [mm/s]; podwyższone wartości sygnalizują zużycie mechaniczne; wartość 0 dla węzłów niebędących pompownią |
| `valveOpen` | boolean | Stan zaworu głównego węzła — otwarty lub zamknięty |

### 1.2 Słownik — `network_nodes.json`

| Pole | Typ | Opis |
|---|---|---|
| **`nodeId`** | String | Identyfikator węzła — klucz łączący ze strumieniem |
| `type` | String | Typ węzła: `PUMP_STATION` — przepompownia, `JUNCTION` — węzeł rozdzielczy, `WATER_TOWER` — wieża ciśnień, `CONSUMER` — punkt poboru |
| `zone` | String | Strefa sieci: `A` — centrum, `B` — zachód, `C` — południe |
| `nominalFlowM3h` | double | Projektowe natężenie przepływu dla danego węzła [m³/h] |
| `nominalPressureBar` | double | Projektowe ciśnienie robocze dla danego węzła [bar] |
| `diameterMm` | double | Średnica nominalna przewodu w węźle [mm] |
| `district` | String | Dzielnica, w której węzeł jest zlokalizowany |

Słownik pozwala ocenić zmierzone wartości w kontekście projektowych parametrów sieci. Znając wyłącznie strumień wiemy, że W-NODE-02 odnotował ciśnienie 3,1 bar — nie wiemy jednak, że jego nominał projektowy wynosi 4,6 bar, co oznacza pracę na poziomie 67% normy i jednoznacznie wskazuje na aktywny wyciek. Słownik dostarcza również typu węzła niezbędnego do interpretacji pól pompowni: odczyty `pumpSpeedRpm` i `vibrationMms` są sensowne wyłącznie dla węzłów `PUMP_STATION`, a porównanie przepływu z `nominalFlowM3h` pozwala wykryć anomalie bez znajomości bezwzględnych progów.

---

## 2. Zadanie projektowe

### Parametry sterujące

| Parametr | Wartość domyślna | Dziedzinowa interpretacja |
|---|---|---|
| `generator.interval.ms` | 8 000 | Odstęp między kolejnymi odczytami czujników — każdy węzeł emituje pomiar co 8 sekund |
| `generator.anomaly.probability` | 0,04 | Prawdopodobieństwo, że dany odczyt pochodzi z epizodu awarii (wyciek lub uszkodzenie pompy) — statystycznie co 25. odczyt z węzła |
| `generator.disorder.max.ms` | 30 000 | Maksymalne rozproszenie czasowe zdarzeń w temacie Kafka — dane wysyłane są natychmiast, lecz wskutek warunków sieciowych mogą docierać w kolejności innej niż chronologiczna, z odchyleniem do 30 sekund względem czasu pomiaru |

### Poziom 1 — parametry per węzeł i pasmo zapotrzebowania (okno 1-minutowe)

Analiza prowadzona jest w rozłącznych oknach jednominutowych, osobno dla każdej kombinacji węzła i pasma zapotrzebowania. Pasmo wyznaczane jest bezpośrednio z godziny znacznika czasowego: PEAK — godziny szczytu porannego i wieczornego (6–9 i 18–21), NIGHT — godziny nocne (1–5), NORMAL — pozostałe godziny doby. Każda minuta należy do dokładnie jednego pasma, więc klucz etykietuje całe okno, nie dzieli odczytów wewnątrz niego. Każde okno opisuje, w jakim stanie hydraulicznym i jakościowym znajdował się węzeł w danej minucie i danym paśmie dobowym. Miary są proste i niewymagające stanu między oknami.

**Miara 1 — Średnie natężenie przepływu**

Średnia wartości `flowM3h` ze wszystkich odczytów danego węzła w oknie minutowym. Natężenie przepływu jest podstawowym wskaźnikiem aktywności węzła — w zestawieniu z `nominalFlowM3h` ze słownika pozwala na wyższym poziomie analizy ocenić, czy sieć pracuje zgodnie z projektem w danym paśmie zapotrzebowania. Objętość wody przepływającą przez węzeł w oknie można wyliczyć jako iloczyn tej miary i długości okna wyrażonej w godzinach.

**Miara 2 — Średnie ciśnienie**

Średnia wartości `pressureBar` w oknie minutowym. Ciśnienie jest kluczowym parametrem diagnostycznym sieci wodociągowej — jego trwały spadek poniżej wartości nominalnej sygnalizuje wyciek lub awarię. Wartość bezwzględna zachowana dla porównania z `nominalPressureBar` ze słownika na wyższym poziomie analizy.

**Miara 3 — Średnie stężenie chloru**

Średnia wartości `chlorineMgL` w oknie minutowym. Chlor jest podstawowym wskaźnikiem sanitarnym wody pitnej — norma wynosi 0,2–0,5 mg/L. Zbyt niskie stężenie oznacza niedostateczną dezynfekcję, zbyt wysokie jest niepożądane smakowo i zapachowo. Miara pozwala śledzić, czy stężenie utrzymuje się w normie w poszczególnych strefach sieci i pasmach dobowych.

**Miara 4 — Maksymalna mętność**

Maksymalna wartość `turbidityNTU` spośród odczytów w oknie minutowym. Szczytowe wartości mętności — w odróżnieniu od średniej — ujawniają chwilowe epizody pogorszenia jakości wody, które mogą towarzyszyć naruszeniu przewodu lub wtargnięciu zanieczyszczeń. Zachowana jako wartość bezwzględna pozwalająca na wyznaczenie dobowego ekstremum per strefa.

> Wyniki tego poziomu powinny być możliwe do dalszego przetwarzania oraz do obserwacji za pomocą narzędzi zewnętrznych (najnowsze dane dla każdego węzła i pasma zapotrzebowania).

### Wzbogacenie kontekstem słownikowym

Minutowe wyniki per węzeł i pasmo łączone są ze słownikiem węzłów przez `nodeId`. Wzbogacenie dostarcza pola `zone` — klucza grupowania na poziomie dobowym — oraz `nominalPressureBar` i `nominalFlowM3h`, które pozwalają wyrazić zmierzone wartości jako ułamek wartości projektowej. Na tym etapie wyznaczana jest również flaga wycieku jako przekroczenie progu 70% ciśnienia nominalnego.

### Poziom 2 — dzienny raport per strefa i pasmo zapotrzebowania (narastająco w ciągu doby)

Analiza dzienna budowana jest wyłącznie na podstawie wzbogaconych wyników minutowych — bez powrotu do surowego strumienia odczytów. Grupowanie po kombinacji `zone` i pasma zapotrzebowania. Każda strefa dostarcza w ciągu doby setek obserwacji minutowych ze wszystkich należących do niej węzłów w każdym paśmie. Wyniki aktualizowane są przy każdym napływającym wyniku minutowym.

**Miara 1 — Średnie natężenie przepływu per strefa i pasmo**

Narastająca średnia natężeń przepływu ze wszystkich minut i węzłów danej strefy w danym paśmie zapotrzebowania. Pozwala porównywać, jak zmienia się obciążenie hydrauliczne poszczególnych stref w ciągu doby — czy strefa centrum (A) pracuje z wyższym przepływem w godzinach szczytu niż strefa południowa (C), i czy wzorzec dobowy jest powtarzalny między kolejnymi dobami.

**Miara 2 — Średni stopień wykorzystania ciśnienia nominalnego**

Narastająca średnia ilorazu zmierzonego ciśnienia i ciśnienia nominalnego per węzeł ze wszystkich minut i węzłów danej strefy w danym paśmie. Wartość bliska 1,0 oznacza pracę zgodną z projektem; systematyczne obniżenie poniżej 0,85 w całej strefie sugeruje strukturalny problem z utrzymaniem ciśnienia, a nie incydent w pojedynczym węźle. Miara agreguje odchylenia różnych węzłów w spójny obraz kondycji hydraulicznej strefy.

**Miara 3 — Liczba aktywnych węzłów per strefa i pasmo w ciągu doby**

Chcemy wiedzieć, ile węzłów w każdej strefie faktycznie dostarczało odczyty w poszczególnych pasmach zapotrzebowania — czy wszystkie czujniki w strefie A pracowały w godzinach nocnych, czy część z nich milczała. Przy kilku węzłach odpowiedź jest oczywista, ale miejska sieć wodociągowa może liczyć tysiące punktów pomiarowych, a ta sama logika musi działać bez zmian przy dowolnej skali. Ze względu na fakt, że przy rozległej sieci dokładne zliczanie unikalnych identyfikatorów węzłów wymaga proporcjonalnie rosnących zasobów, dopuszczalne jest stosowanie metod przybliżonych, które dają odpowiedź z kontrolowanym błędem przy stałym zużyciu pamięci. Wyniki tego poziomu muszą być zapisywane w formie pozwalającej na ich łączenie z wynikami kolejnych dób bez powrotu do danych źródłowych.

> Wyniki mają być dostępne na ujściu najszybciej jak to możliwe.

### Sygnał alarmowy — wyciek w sieci

Wyciek w sieci wodociągowej to niekontrolowane wypływanie wody z uszkodzonego przewodu pod ziemią. Przez wiele godzin lub dni może pozostawać niewykryty wizualnie, prowadząc do strat wody, podmywania gruntu i ryzyka skażenia sieci przez wtargnięcie zanieczyszczeń pod zmniejszonym ciśnieniem. Wczesne wykrycie opiera się na monitorowaniu ciśnienia i jakości wody — oba parametry zmieniają się natychmiast po naruszeniu przewodu.

W strumieniu wyciek manifestuje się spadkiem ciśnienia do około 55% wartości nominalnej (generator ustawia `pressure *= 0.55` przy anomalii) oraz wzrostem mętności do 2–5 NTU wobec normalnych 0,3–0,8 NTU. Przy prawdopodobieństwie anomalii wynoszącym 4% statystycznie co 25. odczyt z węzła pochodzi z epizodu wycieku. Próg alarmowy 70% ciśnienia nominalnego wyznaczany jest post-enrichment na podstawie `nominalPressureBar` ze słownika.

Alarm powinien zostać wyzwolony w dwóch przypadkach. Licznikowy: gdy w dowolnych 5 minutach następujących bezpośrednio po sobie dla jednego węzła pojawią się co najmniej 3 odczyty z ciśnieniem poniżej 70% wartości nominalnej. Natychmiastowy: gdy dla dowolnego węzła wystąpi choćby jeden odczyt z ciśnieniem poniżej 55% wartości nominalnej przy jednoczesnej mętności powyżej 2,0 NTU — połączenie katastrofalnego spadku ciśnienia z pogorszeniem jakości wody wskazuje na poważne naruszenie przewodu z ryzykiem wtargnięcia zanieczyszczeń.

> Sygnały alarmowe powinny być dostępne najszybciej jak to tylko możliwe dla narzędzi zewnętrznych.

# Uwagi ogólne dotyczące implementacji

* Należy zwrócić uwagę na dokonywanie poprawnych obliczeń
* Należy uwzględnić fakt pojawiania się zdarzeń nieuporządkowanych
* Dane słownikowe należy dostarczyć w sposób, aby była możliwa ich zmiana w trakcie przetwarzania
* Aplikacja musi być zabezpieczona przed awariami, tak aby mogła je samodzielnie obsługiwać, lub jeśli to nie możliwe, wznowienie jej działania nie prowadziło do utraty danych. Poziom dostarczanych gwarancji *exactly-once*.
* Ujście musi być trwałą składnicą uwzględniającą wymagane gwarancje oraz umożliwiającą dokonywanie analiz zebranych tam danych (np. agregacji na wyższych poziomach). 
