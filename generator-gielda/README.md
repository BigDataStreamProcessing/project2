# Generator Giełdy Towarowej — Opis Modułu

Moduł generuje syntetyczny strumień zdarzeń giełdy towarowej oraz udostępnia statyczny słownik kontraktów. Dane publikowane są na temat Kafki i mogą służyć jako dane wejściowe dla przetwarzania strumieni danych.

## 1. Opis danych źródłowych

### 1.1 Strumień

Pojedynczy rekord w strumieniu reprezentuje jeden tick cenowy kontraktu terminowego na giełdzie towarowej — chwilową obserwację ceny wraz z informacją o aktywności rynku w tym momencie.

| Pole | Typ | Opis dziedzinowy |
|---|---|---|
| `contractId` | `String` | Identyfikator kontraktu terminowego; klucz łączący ze słownikiem |
| `timestamp` | `Instant` | Czas rejestracji ticka na giełdzie |
| `price` | `double` | Bieżąca cena transakcyjna kontraktu |
| `priceChange` | `double` | Zmiana ceny względem poprzedniego ticka (w jednostkach waluty) |
| `priceChangePct` | `double` | Zmiana ceny względem poprzedniego ticka wyrażona procentowo |
| `bidPrice` | `double` | Najlepsza oferta kupna w chwili ticka |
| `askPrice` | `double` | Najlepsza oferta sprzedaży w chwili ticka |
| `spread` | `double` | Różnica między ofertą sprzedaży a kupna; miara płynności rynku |
| `volumeLots` | `long` | Liczba lotów zarejestrowanych w tym ticku |
| `openInterest` | `long` | Łączna liczba otwartych pozycji na kontrakcie w chwili ticka |
| `volumeValueEur` | `double` | Wartość obrotu ticka przeliczona na EUR |
| `high24h` | `double` | Najwyższa cena z ostatnich 24 godzin |
| `low24h` | `double` | Najniższa cena z ostatnich 24 godzin |
| `settlementPrice` | `double` | Cena rozliczeniowa z poprzedniej sesji; punkt odniesienia do oceny dziennej zmiany |
| `impliedVolatility` | `double` | Implikowana zmienność rynkowa wyrażona w procentach; miara niepewności uczestników rynku |
| `rsi14` | `double` | Wskaźnik siły relatywnej na 14 tickach; sygnalizuje wykupienie lub wyprzedanie rynku |
| `ma20` | `double` | Średnia krocząca z 20 ostatnich ticków; wygładzona linia trendu cenowego |
| `limitUp` | `boolean` | Cena wzrosła o ponad 5% względem poprzedniego ticka — próg zatrzymania notowań |
| `limitDown` | `boolean` | Cena spadła o ponad 5% względem poprzedniego ticka — próg zatrzymania notowań |
| `flashCrash` | `boolean` | Gwałtowna zmiana ceny powyżej 2,5% w jednym ticku przy anomalnym wolumenie |
| `session` | `String` | Faza sesji giełdowej: `PRE_MARKET`, `OPEN`, `CLOSE`, `AFTER_HOURS` |

### 1.2 Słownik

| Pole | Typ | Opis |
|---|---|---|
| `contractId` | `String` | **Klucz łączący ze strumieniem** |
| `commodity` | `String` | Nazwa towaru (np. Pszenica, Rzepak, Kukurydza, Soja) |
| `exchange` | `String` | Giełda, na której notowany jest kontrakt (MATIF, CBOT) |
| `currency` | `String` | Waluta kontraktu (EUR, USD) |
| `unit` | `String` | Jednostka miary towaru (t — tona, bu — buszel) |
| `tickSizeEUR` | `double` | Minimalna zmiana ceny wyrażona w EUR |
| `tickSizeUSD` | `double` | Minimalna zmiana ceny wyrażona w USD |
| `lotSizeT` | `double` | Rozmiar jednego lotu wyrażony w tonach |
| `harvestMonth` | `String` | Miesiąc zbiorów powiązany z kontraktem terminowym |
| `mainExporters` | `List<String>` | Główne kraje eksportujące dany towar na rynki światowe |
| `typicalPriceRangeMin` | `double` | Dolna granica typowego zakresu cenowego dla towaru |
| `typicalPriceRangeMax` | `double` | Górna granica typowego zakresu cenowego dla towaru |

Połączenie strumienia ze słownikiem przez `contractId` przenosi obserwacje cenowe na poziom towaru i giełdy. Bez słownika wiemy, że kontrakt `RPE-AUG25` jest aktywny — nie wiemy jednak, że chodzi o rzepak notowany na MATIF w EUR z lotem o rozmiarze 50 ton. Dopiero ta wiedza umożliwia porównywanie aktywności między giełdami, przeliczanie wolumenu z lotów na tony oraz interpretowanie ruchów cenowych w kontekście typowego zakresu dla danego towaru.

---

## 2. Zadanie projektowe

Giełdy towarowe generują ciągły strumień obserwacji cenowych dla kontraktów terminowych. Celem analizy jest monitorowanie w czasie rzeczywistym, jak kształtuje się aktywność rynkowa dla poszczególnych kontraktów, wykrywanie gwałtownych ruchów cenowych grożących zatrzymaniem notowań oraz budowanie narastających dziennych raportów aktywności per giełda i fazę sesji — pozwalających porównywać rynek europejski z amerykańskim oraz oceniać, jak zmienność rozkłada się w ciągu dnia.

### Parametry sterujące generatorem

| Parametr | Wartość domyślna | Dziedzinowa interpretacja |
|---|---|---|
| `generator.interval.ms` | `1000` | Jeden nowy tick cenowy co sekundę — odpowiada aktywności rynku w trakcie normalnej sesji giełdowej |
| `generator.anomaly.probability` | `0.03` | 3 na każde 100 ticków niesie cechy charakterystyczne dla gwałtownego ruchu cenowego — ponadnormatywny wolumen i nagła zmiana ceny |
| `generator.disorder.max.ms` | `30000` | Maksymalne rozproszenie czasowe zdarzeń w temacie Kafka — dane wysyłane są natychmiast, lecz wskutek warunków sieciowych mogą docierać w kolejności innej niż chronologiczna, z odchyleniem do 30 sekund względem czasu pomiaru |

### Poziom 1 — aktywność per kontrakt i fazę sesji (okno 1-minutowe)

Analiza prowadzona jest w oknach jednominutowych, osobno dla każdej kombinacji kontraktu i fazy sesji (`PRE_MARKET`, `OPEN`, `CLOSE`, `AFTER_HOURS`). Każde okno daje obraz aktywności rynkowej w tej minucie: jak cena się poruszała, ile wolumenu przeszło przez rynek i czy pojawiły się sygnały niepokoju. Faza sesji jest kluczem grupowania, który pozwoli w dalszej analizie porównywać zachowanie rynku w różnych częściach dnia.

**Miara 1 — Zakres cenowy w minucie**
Maksymalna i minimalna cena zaobserwowana w oknie dla danego kontraktu. Uwzględniane są wszystkie ticki — szukamy krańców przedziału, więc żadna obserwacja nie jest pomijana. Zakres cenowy jest fundamentem oceny zmienności i wejściem do klasycznej analizy świecowej.

**Miara 2 — Łączny wolumen obrotu**
Suma liczby lotów ze wszystkich ticków kontraktu w oknie. Wszystkie ticki włączone — wolumen nie ma podtypu. Wysoki wolumen przy spokojnej cenie oznacza inną sytuację rynkową niż wysoki wolumen przy gwałtownym ruchu — zestawienie z Miarą 1 ujawnia ten kontrast.

**Miara 3 — Średni spread bid-ask**
Średnia wartości `spread` w oknie dla danego kontraktu. Wszystkie ticki włączone. Spread jest barometrem płynności — rozszerzający się spread sygnalizuje niepewność animatorów rynku i często poprzedza większy ruch cenowy.

**Miara 4 — Liczba ticków z sygnałem gwałtownej zmiany**
Liczba rekordów w oknie, gdzie `flashCrash == true`. Wszystkie ticki sprawdzane — flaga jest binarna i ustawiana dla zdarzeń z ponadnormatywną zmianą ceny i anomalnym wolumenem. Surowy licznik zachowany celowo — masa danych na poziomie pojedynczego kontraktu jest zbyt mała na stabilną proporcję; proporcja wyłoni się na poziomie dziennym, gdzie masa danych jest wystarczająca.

> Wyniki tego poziomu powinny być możliwe do dalszego przetwarzania oraz do obserwacji za pomocą narzędzi zewnętrznych (najnowsze dane dla każdego kontraktu i fazy sesji).

### Wzbogacenie kontekstem słownikowym

Do minutowych wyników aktywności per kontrakt dołączamy ze słownika pola `commodity`, `exchange`, `currency` oraz `lotSizeT`. Otwiera to trzy nowe wymiary analityczne: pole `exchange` umożliwia grupowanie kontraktów według giełdy i porównywanie MATIF z CBOT; pole `lotSizeT` pozwala wyrazić wolumen w tonach zamiast w lotach — liczbie porównywalnej między kontraktami o różnych rozmiarach lotów; pole `currency` umożliwia interpretowanie wartości w odpowiednim kontekście walutowym.

### Poziom 2 — dzienny raport per giełda i fazę sesji (sesja dzienna, aktualizowany na bieżąco)

Analiza dzienna budowana jest wyłącznie na podstawie wzbogaconych wyników minutowych — bez powrotu do surowego strumienia ticków. Grupowanie po kombinacji giełdy (`exchange`) i fazy sesji (`session`). Wyniki aktualizowane są przy każdym napływającym wyniku minutowym, tworząc narastający obraz sesji od jej otwarcia. Pozwala to na bieżąco odpowiadać na pytanie, czy faza OPEN na MATIF przebiega spokojniej niż na CBOT i czy gwałtowne ruchy skupiają się w określonych godzinach.

**Miara 1 — Łączny wolumen sesji w tonach**
Narastająca suma wolumenu wszystkich kontraktów giełdy w danej fazie sesji, przeliczonego z lotów na tony. Bezpośrednio wyliczalna z minutowych wyników aktywności i rozmiarów lotów ze słownika. Pozwala porównywać rzeczywistą skalę fizycznych przepływów towarów między rynkiem europejskim a amerykańskim w poszczególnych fazach dnia.

**Miara 2 — Udział sygnałów gwałtownej zmiany w danej fazie sesji**
Stosunek liczby minut z co najmniej jednym sygnałem flash crash do łącznej liczby minut obserwowanych dla danej giełdy i fazy. Wyliczalny z minutowych wyników aktywności — mianownik to liczba zakończonych okien minutowych, licznik to okna, w których odnotowano przynajmniej jeden taki sygnał. Pozwala ocenić, czy anomalie skupiają się w fazie OPEN, czy równomiernie rozkładają się przez cały dzień.

**Miara 3 — Typowy minutowy zakres wahań cenowych**
Mediana zakresów cenowych ze wszystkich minut obserwowanych dla danej giełdy i fazy sesji. Chcemy wiedzieć, jaki ruch cenowy był normą — nie jak wypadła średnia, lecz gdzie leży środek rozkładu: czy dominowały spokojne minuty z drobnymi ruchami, czy też rynek przez większą część fazy wykazywał podwyższoną zmienność. Kilka skrajnie gwałtownych minut może znacząco przesunąć średnią, nie zmieniając obrazu tego, co działo się przez resztę czasu — mediana jest na to odporna. Przy rosnącej liczbie kontraktów i coraz dłuższych sesjach wyznaczenie mediany dokładnie staje się kosztowne i wymaga metod przybliżonych. Wyniki tego poziomu muszą być zapisywane w formie pozwalającej na ich późniejsze łączenie z wynikami kolejnych sesji bez powrotu do danych źródłowych.

Wyniki mają być dostępne na ujściu najszybciej jak to możliwe.

### Sygnał alarmowy — Flash Crash

**1. Czym jest zjawisko**
*Flash crash* to gwałtowny, krótkotrwały ruch ceny kontraktu — najczęściej w dół — przekraczający kilka procent wartości w ciągu sekund. Może być wywołany algorytmicznym zleceniem, paniką uczestników rynku lub awarią systemu animatora. W skrajnych przypadkach giełda automatycznie zawiesza notowania (limit up/down), co blokuje możliwość zawierania transakcji po normalnej cenie i może wywołać efekt domina na powiązanych rynkach.

**2. Co obserwujemy w strumieniu**
Generator ustawia flagę `flashCrash = true`, gdy równocześnie zachodzi anomalia cenowa i wolumenowa: zmiana ceny przekracza 2,5% w jednym ticku (normalny ruch to ułamki procentu) przy wolumenie skaczącym do zakresu 5 000–25 000 lotów, wobec normalnego 100–2 100 lotów. Flagi `limitUp` i `limitDown` są ustawiane gdy zmiana przekracza 5% — wielokrotność minimalnego kroku cenowego dla danego kontraktu. Anomalie generowane są z prawdopodobieństwem 3% (`generator.anomaly.probability = 0.03`).

**3. Kiedy bić na alarm**
Alarm gdy w dowolnych 5 minutach następujących bezpośrednio po sobie dla jednego kontraktu pojawią się co najmniej 3 ticki z flagą `flashCrash == true` — lub gdy dla dowolnego kontraktu wystąpi choćby jeden tick z flagą `limitUp == true` albo `limitDown == true`.

Sygnały alarmowe powinny być dostępne najszybciej jak to tylko możliwe dla narzędzi zewnętrznych.

# Uwagi ogólne dotyczące implementacji 

* Należy zwrócić uwagę na dokonywanie poprawnych obliczeń 
* Należy uwzględnić fakt pojawiania się zdarzeń nieuporządkowanych 
* Dane słownikowe należy dostarczyć w sposób, aby była możliwa ich zmiana w trakcie przetwarzania
* Aplikacja musi być zabezpieczona przed awariami, tak aby mogła je samodzielnie obsługiwać, lub jeśli to nie możliwe, wznowienie jej działania nie prowadziło do utraty danych. Poziom dostarczanych gwarancji *exactly-once*.
* Ujście musi być trwałą składnicą uwzględniającą wymagane gwarancje oraz umożliwiającą dokonywanie analiz zebranych tam danych (np. agregacji na wyższych poziomach). 