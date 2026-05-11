# Generator Danych o Jakości Powietrza — Opis Modułu

Generator strumienia odczytów stacji monitoringu jakości powietrza publikowanych do tematu Kafka `powietrze-odczyty`.

---

## 1. Dane źródłowe

### 1.1 Strumień — `powietrze-odczyty`

Pojedynczy rekord reprezentuje automatyczny odczyt czujników stacji monitoringu jakości powietrza: zestaw stężeń zanieczyszczeń atmosferycznych oraz towarzyszące im pomiary meteorologiczne wpływające na dyspersję substancji w powietrzu.

| Pole | Typ | Opis dziedzinowy |
|---|---|---|
| `stationId` | String | Identyfikator stacji monitoringu; klucz do słownika stacji |
| `timestamp` | Instant | Czas wykonania pomiaru (UTC) |
| `pm25` | double | Stężenie pyłu zawieszonego PM2.5 — cząstki o średnicy poniżej 2,5 µm, przenikające głęboko do układu oddechowego [µg/m³] |
| `pm10` | double | Stężenie pyłu zawieszonego PM10 — cząstki o średnicy poniżej 10 µm [µg/m³] |
| `no2` | double | Stężenie dwutlenku azotu — gaz emitowany głównie przez silniki spalinowe i elektrownie [µg/m³] |
| `o3` | double | Stężenie ozonu przyziemnego — wtórny zanieczyszczacz powstający pod wpływem promieniowania UV z reakcji tlenków azotu i lotnych związków organicznych [µg/m³] |
| `co` | double | Stężenie tlenku węgla — bezbarwny gaz powstający przy niepełnym spalaniu paliw [mg/m³] |
| `so2` | double | Stężenie dwutlenku siarki — gaz emitowany przy spalaniu węgla i ropy z wysoką zawartością siarki [µg/m³] |
| `benzene` | double | Stężenie benzenu — lotny związek organiczny, substancja rakotwórcza emitowana przez transport i przemysł [µg/m³] |
| `temperatureC` | double | Temperatura powietrza w otoczeniu stacji [°C] |
| `humidityPercent` | double | Wilgotność względna powietrza [%] |
| `windSpeedMs` | double | Prędkość wiatru [m/s] |
| `windDirectionDeg` | double | Kierunek wiatru w stopniach: 0° = północ, 90° = wschód, 180° = południe, 270° = zachód |
| `boundaryLayerHeightM` | double | Wysokość warstwy granicznej atmosfery — grubość warstwy powietrza, w której zachodzi turbulentne mieszanie zanieczyszczeń [m]; niskie wartości oznaczają słabą dyspersję i sprzyjają kumulacji smogu |


### 1.2 Słownik — `air_stations.json` i `aqi_norms.json`

**Stacje monitoringu** (`air_stations.json`):

| Pole | Typ | Opis |
|---|---|---|
| **`stationId`** | String | Identyfikator stacji — klucz łączący ze strumieniem |
| `name` | String | Pełna nazwa stacji (np. „Kraków Al. Krasińskiego") |
| `lat` | double | Szerokość geograficzna [°] |
| `lon` | double | Długość geograficzna [°] |
| `district` | String | Dzielnica, w której stacja jest zlokalizowana |
| `stationType` | String | Typ stacji według klasyfikacji GIOŚ: `URBAN_TRAFFIC` — przy ruchliwej arterii, `URBAN_BACKGROUND` — reprezentatywna dla tła miejskiego, `SUBURBAN` — podmiejska |

**Normy jakości powietrza** (`aqi_norms.json`):

| Pole | Opis |
|---|---|
| `pm25[].level` | Nazwa kategorii jakości powietrza dla PM2.5 (Dobry / Umiarkowany / Niezdrowy_wrazliwi / Niezdrowy / BardzoNiezdrowy / Hazardous) |
| `pm25[].min`, `pm25[].max` | Przedział stężeń PM2.5 definiujący daną kategorię [µg/m³] |
| `pm25[].aqi` | Wartość liczbowa indeksu AQI odpowiadająca dolnej granicy kategorii |
| `pm10Limit24hEU` | Dobowy limit stężenia PM10 według dyrektywy UE: 50 µg/m³ |
| `no2LimitAnnualEU` | Roczny limit stężenia NO2 według dyrektywy UE: 40 µg/m³ |
| `o3LimitMax8hEU` | Maksymalne dopuszczalne stężenie O3 dla 8-godzinnej średniej kroczącej według UE: 120 µg/m³ |
| `coLimit8hEU` | Maksymalne dopuszczalne stężenie CO dla 8-godzinnej średniej kroczącej według UE: 10 mg/m³ |

Pierwszy słownik (`air_stations.json`) dostarcza kontekstu lokalizacyjnego i typologicznego niezbędnego do porównywania stacji. Znając wyłącznie strumień wiemy, że AIR-KRK-001 zarejestrowała PM2.5 = 78 µg/m³ — nie wiemy jednak, że stacja stoi przy ruchliwej arterii w krakowskim Śródmieściu (`URBAN_TRAFFIC`), co wyjaśnia, dlaczego jej odczyty NO2 systematycznie przekraczają wartości sąsiednich stacji tła. 

Drugi słownik (`aqi_norms.json`) nadaje odczytom interpretację normatywną: to samo stężenie 78 µg/m³ plasuje się w kategorii „Niezdrowy" i przekracza progi określone przez UE — informacji tej nie można odtworzyć bez dostępu do norm.

---

## 2. Zadanie projektowe

### Parametry sterujące

| Parametr | Wartość domyślna | Dziedzinowa interpretacja |
|---|---|---|
| `generator.interval.ms` | 10 000 | Odstęp między kolejnymi odczytami — każda stacja emituje jeden pomiar co 10 sekund |
| `generator.anomaly.probability` | 0,08 | Prawdopodobieństwo, że dany odczyt pochodzi z epizodu smogowego — statystycznie co 12. pomiar ze stacji |
| `generator.disorder.max.ms` | 30 000 | Maksymalne rozproszenie czasowe zdarzeń w temacie Kafka — dane wysyłane są natychmiast, lecz wskutek warunków sieciowych mogą docierać w kolejności innej niż chronologiczna, z odchyleniem do 30 sekund względem czasu pomiaru |

### Poziom 1 — stężenia per stacja i sektor wiatru (okno 1-minutowe)

Analiza prowadzona jest w rozłącznych oknach jednominutowych, osobno dla każdej kombinacji stacji i sektora wiatru. Sektor wiatru wyznaczany jest bezpośrednio z pola `windDirectionDeg` jako jedna z czterech stron świata. Każde okno opisuje, jakie stężenia zanieczyszczeń towarzyszyły danemu kierunkowi napływu powietrza na danej stacji przez tę minutę. 

**Miara 1 — Średnie stężenie PM2.5**

Średnia wartości `pm25` ze wszystkich odczytów danej stacji w danym sektorze wiatru w oknie minutowym. Pył PM2.5 jest najistotniejszym wskaźnikiem zdrowotnym spośród mierzonych zanieczyszczeń — jego drobne cząstki przenikają do krwi przez płuca. Wartość ta zasila późniejsze porównanie poziomów zanieczyszczenia w zależności od kierunku napływu powietrza i rodzaju zabudowy.

**Miara 2 — Średnie stężenie NO2**

Średnia wartości `no2` w oknie minutowym. Dwutlenek azotu pochodzi głównie z transportu drogowego, przez co stacje przy arteriach komunikacyjnych rejestrują jego stężenia wielokrotnie wyższe niż stacje tła miejskiego. Miara uzupełnia obraz PM2.5 o składową emisji komunikacyjnej i pozwala rozróżniać źródła zanieczyszczenia na wyższym poziomie analizy.

**Miara 3 — Maksymalne stężenie PM10**

Maksymalna wartość `pm10` spośród odczytów w oknie minutowym. Szczytowe stężenia PM10 — w odróżnieniu od średniej — ujawniają chwilowe epizody pylenia, często związane z przejazdem pojazdów ciężkich lub wiatrem unoszącym pył z nawierzchni. Zachowana jako wartość bezwzględna pozwalająca na wyznaczenie dobowego ekstremum na wyższym poziomie analizy.

**Miara 4 — Średnia wysokość warstwy granicznej**

Średnia wartości `boundaryLayerHeightM` w oknie minutowym. Wysokość warstwy granicznej określa, w jakiej objętości powietrza rozpraszają się emitowane zanieczyszczenia: przy wysokości 200 m stężenia mogą być pięciokrotnie wyższe niż przy 1000 m przy tej samej emisji u źródła. Zestawienie tej miary z poziomami PM2.5 na wyższym poziomie analizy pozwala oddzielić epizody smogowe wynikające z wysokiej emisji od tych spowodowanych złymi warunkami meteorologicznymi.

> Wyniki tego poziomu powinny być możliwe do dalszego przetwarzania oraz do obserwacji za pomocą narzędzi zewnętrznych (najnowsze dane dla każdej stacji i sektora wiatru).

### Wzbogacenie kontekstem słownikowym

Minutowe wyniki per stacja i sektor wiatru łączone są ze słownikiem stacji przez `stationId`. Wzbogacenie dostarcza pola `stationType` — klucza grupowania na poziomie dobowym — oraz `district`, który pozwala przypisać wyniki do konkretnej dzielnicy miasta. Normy jakości powietrza (`aqi_norms.json`) wykorzystywane są wyłącznie w ścieżce detekcji sygnałów alarmowych.

### Poziom 2 — dzienny raport per typ stacji i sektor wiatru (narastająco w ciągu doby)

Analiza dzienna budowana jest wyłącznie na podstawie wzbogaconych wyników minutowych — bez powrotu do surowego strumienia odczytów. Grupowanie po kombinacji `stationType` i sektora wiatru. Każdy typ stacji dostarcza w ciągu doby setek obserwacji minutowych ze wszystkich należących do niego stacji i dla wszystkich rejestrowanych sektorów wiatru. Wyniki aktualizowane są przy każdym napływającym wyniku minutowym.

**Miara 1 — Średnie dobowe stężenie PM2.5**

Narastająca średnia stężeń PM2.5 ze wszystkich minut i stacji danego typu przy danym sektorze wiatru w bieżącej dobie. Pozwala odpowiedzieć na pytanie, czy stacje przy arteriach komunikacyjnych rejestrują wyższe stężenia przy wietrze z południa niż z północy — co może wskazywać na kierunek napływu zanieczyszczeń z konkretnych źródeł obszarowych.

**Miara 2 — Średnia dobowa wysokość warstwy granicznej**

Narastająca średnia wysokości warstwy granicznej ze wszystkich minut i stacji danego typu przy danym sektorze wiatru. Zestawiona z Miarą 1 ujawnia, czy podwyższone stężenia PM2.5 przy danym sektorze wynikają z niekorzystnych warunków meteorologicznych (niska warstwa graniczna), czy z napływu zanieczyszczonego powietrza ze źródeł zewnętrznych — mimo dobrej cyrkulacji stężenia pozostają wysokie. To rozróżnienie jest kluczowe dla prawidłowej klasyfikacji epizodów smogowych.

**Miara 3 — Liczba stacji, które zarejestrowały każdy sektor wiatru w ciągu doby**

Chcemy wiedzieć, ile stacji danego typu aktywnie mierzyło przy wietrze z każdego kierunku w ciągu doby — czy wszystkie stacje tła miejskiego dostarczyły dane przy wietrze wschodnim, czy tylko część z nich. Przy kilku stacjach odpowiedź jest oczywista, ale krajowa sieć monitoringu GIOŚ liczy setki punktów pomiarowych, a ta sama logika musi działać bez zmian przy dowolnej skali. Ze względu na fakt, że przy rozległej sieci stacji dokładne zliczanie unikalnych identyfikatorów wymaga proporcjonalnie rosnących zasobów, dopuszczalne jest stosowanie metod przybliżonych, które dają odpowiedź z kontrolowanym błędem przy stałym zużyciu pamięci. Wyniki tego poziomu muszą być zapisywane w formie pozwalającej na ich łączenie z wynikami kolejnych dób bez powrotu do danych źródłowych.

Wyniki mają być dostępne na ujściu najszybciej jak to możliwe.

### Sygnał alarmowy — epizod smogowy

Epizod smogowy to stan, w którym stężenie pyłu zawieszonego PM2.5 przekracza poziom uznany za bezpieczny dla zdrowia człowieka i utrzymuje się przez dłuższy czas. W odróżnieniu od chwilowych skoków stężeń, epizod smogowy charakteryzuje się trwałością — jest skutkiem kumulacji zanieczyszczeń przy jednoczesnej słabej cyrkulacji powietrza, typowej dla wyżu barycznego w sezonie grzewczym.

W strumieniu epizod smogowy manifestuje się stężeniami PM2.5 w przedziale 60–160 µg/m³ (przy prawdopodobieństwie anomalii wynoszącym 8%), którym towarzyszy niska wysokość warstwy granicznej (100–300 m wobec normalnych 500–1500 m). Próg kategorii „Niezdrowy" wynosi 55 µg/m³, a kategorii „BardzoNiezdrowy" — 150 µg/m³, zgodnie z `aqi_norms.json`. Detekcja przebiega na strumieniu wzbogaconym o progi normatywne.

Alarm powinien zostać wyzwolony w dwóch przypadkach. Licznikowy: gdy w dowolnych 5 minutach następujących bezpośrednio po sobie dla jednej stacji co najmniej 3 odczyty wykażą stężenie PM2.5 powyżej 55 µg/m³. Natychmiastowy: gdy dla dowolnej stacji wystąpi choćby jeden odczyt ze stężeniem PM2.5 powyżej 150 µg/m³.

Sygnały alarmowe powinny być dostępne najszybciej jak to tylko możliwe dla narzędzi zewnętrznych.

# Uwagi ogólne dotyczące implementacji

* Należy zwrócić uwagę na dokonywanie poprawnych obliczeń
* Należy uwzględnić fakt pojawiania się zdarzeń nieuporządkowanych
* Dane słownikowe należy dostarczyć w sposób, aby była możliwa ich zmiana w trakcie przetwarzania
* Aplikacja musi być zabezpieczona przed awariami, tak aby mogła je samodzielnie obsługiwać, lub jeśli to nie możliwe, wznowienie jej działania nie prowadziło do utraty danych. Poziom dostarczanych gwarancji *exactly-once*.
* Ujście musi być trwałą składnicą uwzględniającą wymagane gwarancje oraz umożliwiającą dokonywanie analiz zebranych tam danych (np. agregacji na wyższych poziomach). 
