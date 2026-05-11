# Generator Danych Pogodowych — Opis Modułu

Generator strumienia odczytów stacji meteorologicznych publikowanych do tematu Kafka `pogoda-odczyty`.

---

## 1. Dane źródłowe

### 1.1 Strumień — `pogoda-odczyty`

Pojedynczy rekord reprezentuje automatyczny odczyt instrumentów stacji meteorologicznej: zestaw wartości zmierzonych w jednej chwili, opisujących aktualny stan atmosfery w miejscu lokalizacji stacji.

| Pole | Typ | Opis dziedzinowy |
|---|---|---|
| `stationId` | String | Identyfikator stacji meteorologicznej; klucz do słownika stacji |
| `timestamp` | Instant | Czas wykonania pomiaru (UTC) |
| `temperatureC` | double | Temperatura powietrza mierzona na standardowej wysokości 2 m n.p.g. [°C] |
| `feelsLikeC` | double | Temperatura odczuwalna — temperatura powietrza korygowana o efekt chłodzenia wiatrem [°C] |
| `dewPointC` | double | Temperatura punktu rosy — temperatura, przy której powietrze o zmierzonej wilgotności osiągnęłoby nasycenie [°C] |
| `humidityPercent` | double | Wilgotność względna powietrza [%] |
| `pressureHpa` | double | Ciśnienie atmosferyczne [hPa] |
| `pressureTrendHpaH` | double | Tendencja baryczna — zmiana ciśnienia ekstrapolowana na jedną godzinę na podstawie bieżącego trendu [hPa/h]; wartości ujemne oznaczają spadek ciśnienia |
| `windSpeedMs` | double | Uśredniona prędkość wiatru w interwale pomiarowym [m/s] |
| `windGustMs` | double | Maksymalna chwilowa prędkość wiatru (poryw) w interwale pomiarowym [m/s] |
| `windDirectionDeg` | double | Kierunek wiatru w stopniach: 0° = północ, 90° = wschód, 180° = południe, 270° = zachód |
| `precipitationMmH` | double | Intensywność opadów atmosferycznych w interwale pomiarowym [mm/h] |
| `snowDepthCm` | double | Grubość pokrywy śnieżnej mierzona przy stacji [cm]; wartość niezerowa wyłącznie przy temperaturze powietrza poniżej 0 °C |
| `visibilityKm` | double | Pozioma widzialność atmosferyczna — maksymalna odległość, na jaką można dostrzec wyraźny obiekt [km] |
| `solarRadiationWm2` | double | Natężenie całkowitego promieniowania słonecznego padającego na powierzchnię poziomą [W/m²] |
| `uvIndex` | double | Indeks UV — bezwymiarowa miara natężenia biologicznie czynnego promieniowania ultrafioletowego |
| `lightningCount10min` | int | Liczba wyładowań atmosferycznych zarejestrowanych przez lokalny detektor w bieżącym odczycie |
| `weatherCode` | String | Kod zjawiska atmosferycznego według klasyfikacji WMO: CLR — bezchmurnie, FEW / SCT / BKN / OVC — kolejne stopnie zachmurzenia, RA — deszcz, TSRA — burza z deszczem, SN — śnieg, FG — mgła, HZ — zamglenie |

### 1.2 Słownik — `stations.json`

| Pole | Typ | Opis |
|---|---|---|
| **`stationId`** | String | Identyfikator stacji — klucz łączący ze strumieniem |
| `name` | String | Pełna nazwa stacji (np. „Kraków Balice") |
| `lat` | double | Szerokość geograficzna [°] |
| `lon` | double | Długość geograficzna [°] |
| `altitudeM` | double | Wysokość stacji nad poziomem morza [m n.p.m.] |
| `region` | String | Województwo, na terenie którego stacja jest zlokalizowana |
| `climateZone` | String | Strefa klimatyczna według klasyfikacji Köppena (np. Dfb — umiarkowany wilgotny, Dfc — subpolarny wilgotny) |

Słownik pozwala osadzić odczyt instrumentalny w geograficznym i klimatycznym kontekście. Znając wyłącznie strumień wiemy, że stacja WAW-001 zarejestrowała 14,2 °C i kod zjawiska RA — nie wiemy jednak, że jest to stacja w województwie mazowieckim, ani że pracuje w strefie klimatycznej Dfb, co ma znaczenie przy ocenie normalności tych wartości na tle innych stacji w regionie. Słownik dostarcza również wysokości stacji: ZAK-001 (Zakopane, 844 m n.p.m.) pracuje w strefie Dfc, gdzie te same wartości temperatury i ciśnienia mają zupełnie inną interpretację klimatyczną niż dla nizinnej POZ-001 (Poznań Ławica, 83 m n.p.m.).

---

## 2. Zadanie projektowe

### Parametry sterujące

| Parametr | Wartość domyślna | Dziedzinowa interpretacja |
|---|---|---|
| `generator.interval.ms` | 10 000 | Odstęp między kolejnymi odczytami instrumentów — każda stacja emituje jeden pomiar co 10 sekund |
| `generator.anomaly.probability` | 0,04 | Prawdopodobieństwo, że dany odczyt pochodzi z epizodu ekstremalnego (gwałtowna burza) — statystycznie co 25. pomiar ze stacji |
| `generator.disorder.max.ms` | 30 000 | Maksymalne rozproszenie czasowe zdarzeń w temacie Kafka — dane wysyłane są natychmiast, lecz wskutek warunków sieciowych mogą docierać w kolejności innej niż chronologiczna, z odchyleniem do 30 sekund względem czasu pomiaru |

### Poziom 1 — odczyty per stacja i zjawisko (okno 1-minutowe)

Analiza prowadzona jest w rozłącznych oknach jednominutowych, osobno dla każdej kombinacji stacji i kodu zjawiska atmosferycznego. Każde okno opisuje, jakie warunki panowały na danej stacji podczas danego zjawiska przez tę minutę: jaka była temperatura, ile spadło opadów, jak silny był wiatr i czy wystąpiły wyładowania. Miary są proste i niewymagające stanu między oknami.

**Miara 1 — Średnia temperatura powietrza**

Średnia wartości `temperatureC` ze wszystkich odczytów danej stacji zakwalifikowanych pod dany kod zjawiska w oknie minutowym. Temperatura jest podstawowym parametrem diagnostycznym warunkującym interpretację pozostałych odczytów — ten sam kod RA (deszcz) przy −1 °C oznacza opady marznące, przy 15 °C typowy opad wiosenny. Wartość ta zasila późniejsze wyznaczanie rozkładu temperatur w skali województwa i doby.

**Miara 2 — Suma opadów**

Suma wartości `precipitationMmH` ze wszystkich odczytów w oknie minutowym. Łączna ilość wody dostarczona przez dane zjawisko w danej minucie stanowi bezpośredni wkład do bilansu opadowego i dlatego wyrażona jest jako wartość bezwzględna umożliwiająca sumowanie na poziomie dobowym.

**Miara 3 — Maksymalna prędkość porywy wiatru**

Maksymalna wartość `windGustMs` spośród odczytów w oknie minutowym. Poryw — a nie prędkość średnia — decyduje o przekroczeniu progów alarmowych i jest miarodajnym wskaźnikiem chwilowego obciążenia infrastruktury. Zachowana jako wartość bezwzględna pozwalająca na wyznaczenie regionalnego ekstremum na poziomie dobowym.

**Miara 4 — Liczba odczytów z wyładowaniami atmosferycznymi**

Liczba odczytów w oknie minutowym, w których detektor zarejestrował co najmniej jedno wyładowanie atmosferyczne (`lightningCount10min > 0`). Surowy licznik zachowany jako wartość bezwzględna — proporcja aktywności burzowej dla województwa wyliczana jest na poziomie dobowym po połączeniu wyników z wielu stacji.

> Wyniki tego poziomu powinny być możliwe do dalszego przetwarzania oraz do obserwacji za pomocą narzędzi zewnętrznych (najnowsze dane dla każdej stacji i zarejestrowanego zjawiska).

### Wzbogacenie kontekstem słownikowym

Minutowe wyniki per stacja i zjawisko łączone są ze słownikiem przez `stationId`. Wzbogacenie dostarcza pola `region` — klucza grupowania na poziomie dobowym — oraz `climateZone`, który pozwala interpretować zmierzone wartości w kontekście typowych warunków klimatycznych dla danej stacji. Pole `altitudeM` umożliwia dodatkowe przeliczenia ciśnienia do poziomu morza przy porównywaniu stacji o różnych wysokościach.

### Poziom 2 — dzienny raport per województwo i zjawisko (narastająco w ciągu doby)

Analiza dzienna budowana jest wyłącznie na podstawie wzbogaconych wyników minutowych — bez powrotu do surowego strumienia odczytów. Grupowanie po kombinacji `region` i `weatherCode`. Każde województwo dostarcza w ciągu doby tysiące obserwacji minutowych ze wszystkich pracujących w nim stacji i dla wszystkich rejestrowanych zjawisk, co zapewnia wystarczającą masę danych dla analizy rozkładu. Wyniki aktualizowane są przy każdym napływającym wyniku minutowym.

**Miara 1 — Łączna suma opadów per województwo i zjawisko**

Narastająca suma opadów ze wszystkich minut i stacji danego województwa przy danym kodzie zjawiska w bieżącej dobie. Pozwala porównywać, ile wody dostarczyło dane zjawisko w poszczególnych województwach oraz jak rozkłada się aktywność opadowa w ciągu dnia.

**Miara 2 — Odsetek okien minutowych z aktywnością burzową**

Udział okien minutowych z kodem TSRA, w których odnotowano co najmniej jeden odczyt z wyładowaniami, wśród wszystkich okien minutowych z kodem TSRA dla danego województwa i doby. Odpowiada na pytanie, jak długo w ciągu dnia burza była faktycznie aktywna nad województwem — nie tylko jak często pojawiła się w strumieniu.

**Miara 3 — Liczba stacji, które zarejestrowały dane zjawisko w ciągu doby**

Chcemy wiedzieć, jak szeroki był zasięg przestrzenny danego zjawiska atmosferycznego w województwie — czy mgła objęła jedną stację na krańcu regionu, czy wszystkie równocześnie. Przy kilku stacjach odpowiedź jest trywialna, ale sieć pomiarowa rozrasta się: IMGW eksploatuje dziesiątki stacji synoptycznych i setki automatycznych, a ta sama logika musi działać bez zmian przy dowolnej skali. Ze względu na fakt, że przy dużej sieci stacji pomiarowych obliczenia te wymagają proporcjonalnie rosnącej pamięci, dopuszczalne jest wykorzystanie algorytmów heurystycznych, które dają odpowiedź z kontrolowanym błędem przy stałym zużyciu zasobów. Wyniki tego poziomu muszą być zapisywane w formie pozwalającej na ich łączenie z wynikami kolejnych dób bez powrotu do danych źródłowych.

Wyniki mają być dostępne na ujściu najszybciej jak to możliwe.

### Sygnał alarmowy — gwałtowna burza konwekcyjna

Gwałtowna burza konwekcyjna to zjawisko, w którym ciepłe, wilgotne powietrze gwałtownie unosi się ku górze, tworząc komórkę burzową zdolną do generowania ekstremalnych opadów, silnych porywów wiatru i wyładowań elektrycznych w ciągu kilku do kilkunastu minut. Dla służb meteorologicznych i operatorów infrastruktury krytycznej kluczowe jest wykrycie jej wczesnych symptomów, zanim zjawisko osiągnie pełną intensywność.

W strumieniu epizod ekstremalny manifestuje się jednocześnie nagłą tendencją baryczną wynoszącą −6,5 hPa/h (ciśnienie opada szybciej niż 6 hPa na godzinę), prędkością porywy wiatru w przedziale 22–37 m/s, intensywnością opadów sięgającą 0–40 mm/h oraz pojawieniem się wyładowań atmosferycznych przy opadach przekraczających 20 mm/h. Zdarzenia anomalne generowane są z prawdopodobieństwem 4% (`generator.anomaly.probability = 0.04`), przy czym nie każdy epizod anomalny jednocześnie przekracza wszystkie progi — silny wiatr może towarzyszyć śladowym opadom.

Alarm powinien zostać wyzwolony w dwóch przypadkach. Licznikowy: gdy w dowolnych 5 minutach następujących bezpośrednio po sobie dla jednej stacji pojawią się co najmniej 3 odczyty z prędkością porywy wiatru powyżej 25 m/s oraz intensywnością opadów powyżej 20 mm/h. Natychmiastowy: gdy dla dowolnej stacji wystąpi choćby jeden odczyt z tendencją baryczną poniżej −6 hPa/h przy jednoczesnym pojawieniu się wyładowań atmosferycznych (`lightningCount10min > 0`).

Detekcja przebiega bezpośrednio na strumieniu — wszystkie progi alarmowe są wartościami bezwzględnymi niezależnymi od słownika stacji.

Sygnały alarmowe powinny być dostępne najszybciej jak to tylko możliwe dla narzędzi zewnętrznych.

# Uwagi ogólne dotyczące implementacji

* Należy zwrócić uwagę na dokonywanie poprawnych obliczeń
* Należy uwzględnić fakt pojawiania się zdarzeń nieuporządkowanych
* Dane słownikowe należy dostarczyć w sposób, aby była możliwa ich zmiana w trakcie przetwarzania
* Aplikacja musi być zabezpieczona przed awariami, tak aby mogła je samodzielnie obsługiwać, lub jeśli to nie możliwe, wznowienie jej działania nie prowadziło do utraty danych. Poziom dostarczanych gwarancji *exactly-once*.
* Ujście musi być trwałą składnicą uwzględniającą wymagane gwarancje oraz umożliwiającą dokonywanie analiz zebranych tam danych (np. agregacji na wyższych poziomach). 