# generator-ecommerce

Moduł generuje syntetyczny strumień zdarzeń transakcyjnych sklepu internetowego oraz udostępnia statyczny słownik produktów. Dane publikowane są na temat Kafki i mogą służyć jako dane wejściowe dla przetwarzania strumieni danych.

---

## 1. Opis danych źródłowych

### 1.1 Strumień zdarzeń — `OrderEvent`

Każdy rekord w strumieniu reprezentuje jedno zdarzenie w cyklu życia zamówienia złożonego przez klienta sklepu. Zdarzenia napływają w czasie rzeczywistym i mogą docierać z niewielkim opóźnieniem względem czasu ich faktycznego wystąpienia (patrz parametr `generator.disorder.max.ms`).

| Pole | Typ | Opis dziedzinowy |
|---|---|---|
| `orderId` | `String` | Unikalny identyfikator zamówienia — pozwala śledzić to samo zamówienie przez kolejne etapy jego realizacji |
| `userId` | `String` | Identyfikator zarejestrowanego klienta, który złożył zamówienie |
| `sessionId` | `String` | Identyfikator sesji zakupowej — jedna sesja może obejmować wiele przeglądanych produktów, ale zazwyczaj kończy się jednym zamówieniem |
| `timestamp` | `Instant` | Czas zdarzenia po stronie klienta (może być wcześniejszy niż czas odbioru przez system) |
| `eventType` | `String` | Etap w cyklu życia zamówienia: `ORDER_PLACED` — klient potwierdził koszyk, `PAYMENT_OK` — płatność rozliczona pomyślnie, `PAYMENT_FAILED` — płatność odrzucona, `RETURN` — klient zgłosił zwrot towaru |
| `items[]` | `List` | Koszyk zakupowy — lista pozycji, każda opisana osobnym rekordem (patrz niżej) |
| `items[].productId` | `String` | Identyfikator produktu — klucz łączący z katalogiem produktów |
| `items[].quantity` | `int` | Liczba sztuk danego produktu w tej pozycji koszyka |
| `items[].unitPricePLN` | `double` | Cena jednostkowa zapłacona przez klienta (może odbiegać od ceny katalogowej wskutek promocji) |
| `items[].lineTotalPLN` | `double` | Wartość pozycji koszyka: ilość × cena jednostkowa |
| `totalAmountPLN` | `double` | Łączna kwota zamówienia po odjęciu rabatu — wartość faktycznie obciążająca klienta |
| `discountAmountPLN` | `double` | Wartość przyznanego rabatu; zero oznacza brak promocji |
| `couponCode` | `String` | Kod kuponu użytego przy zamówieniu; `null` gdy zamówienie bez promocji |
| `paymentMethod` | `String` | Forma płatności wybrana przez klienta: `CARD` — karta płatnicza, `BLIK` — kod BLIK, `TRANSFER` — przelew, `PAYPAL` — portfel PayPal |
| `paymentStatus` | `String` | Wynik próby rozliczenia płatności: `SUCCESS` — środki pobrane, `FAILED` — płatność odrzucona, `PENDING` — oczekuje na potwierdzenie |
| `cardBin` | `String` | Pierwsze 6 cyfr numeru karty — identyfikują bank wydający i typ karty; dostępne wyłącznie dla płatności kartą; nie są daną wrażliwą w rozumieniu PCI-DSS |
| `shippingCountry` | `String` | Kraj dostawy (kod ISO) |
| `shippingCity` | `String` | Miasto dostawy |
| `deviceType` | `String` | Rodzaj urządzenia, z którego złożono zamówienie: `MOBILE`, `DESKTOP`, `TABLET` |
| `browserFamily` | `String` | Rodzina przeglądarki internetowej klienta |
| `fraudScore` | `double` | Ocena ryzyka nadużycia przypisana zdarzeniu przez system, w skali 0,0–1,0; wartości bliskie 1,0 oznaczają wysokie prawdopodobieństwo fraudu |
| `isFraudSuspected` | `boolean` | Flaga ustawiana automatycznie gdy `fraudScore > 0,65`; sygnalizuje zdarzenie wymagające weryfikacji przez dział bezpieczeństwa |

### 1.2 Słownik produktów — `ProductCatalog`

Słownik zawiera statyczny opis każdego produktu dostępnego w sklepie. Ładowany jest jednorazowo przy starcie generatora z pliku `resources/dictionary/products.json`.

| Pole | Typ | Opis dziedzinowy |
|---|---|---|
| `productId` | `String` | **Klucz łączący ze strumieniem** — odpowiada polu `items[].productId` w zdarzeniu; pozwala przypisać każdej pozycji koszyka pełen opis produktu |
| `name` | `String` | Pełna nazwa handlowa produktu widoczna dla klienta |
| `category` | `String` | Główna kategoria asortymentowa (np. `Elektronika`, `AGD`, `Odzież`) |
| `subcategory` | `String` | Węższa kategoria w ramach działu (np. `Komputery`, `Ekspresy`, `Kurtki`) |
| `basePricePLN` | `double` | Katalogowa cena detaliczna produktu w złotych |
| `marginPercent` | `double` | Marża handlowa sklepu na tym produkcie wyrażona w procentach — informuje, jaka część przychodu ze sprzedaży staje się zyskiem |
| `weightKg` | `double` | Masa produktu w kilogramach — istotna przy kalkulacji kosztów logistyki i limitów wagowych przesyłek |
| `fraudRiskScore` | `double` | Historycznie wyznaczone ryzyko fraudu dla tej grupy produktów, w skali 0,0–1,0; wysokie wartości (np. `0,7` dla laptopów) oznaczają, że produkt jest często celem fałszywych zamówień |

**Dlaczego połączenie `productId` ma sens analitycznie?**
Sam strumień mówi nam *ile* wydano i *na jaki identyfikator* — ale nie mówi nic o tym, z jakiego działu pochodzi produkt, jaka jest jego marżowość ani czy historycznie przyciąga nieuczciwe zamówienia. Dopiero po dołączeniu słownika możemy zadawać pytania o rentowność sprzedaży w podziale na kategorie oraz oceniać, czy wysoki `fraudScore` w zdarzeniu jest spójny z ryzykiem przypisanym konkretnemu produktowi.

---

## 2. Zadanie projektowe

Sklep internetowy przetwarza tysiące zamówień dziennie. Dział analityczny potrzebuje bieżącego wglądu w to, które produkty i kategorie sprzedają się najlepiej, gdzie tracone są przychody przez nieudane płatności oraz — co krytyczne — gdzie pojawiają się oznaki wyłudzeń. Poniższe zadanie opisuje, jak zbudować taki system obserwacji w oparciu o strumień zdarzeń.

### Parametry sterujące generatorem

| Parametr | Wartość domyślna | Dziedzinowa interpretacja |
|---|---|---|
| `generator.interval.ms` | `2000` | Jedno nowe zdarzenie transakcyjne co 2 sekundy — odpowiada ruchowi sklepu średniej wielkości w godzinach szczytu |
| `generator.users.count` | `500` | Pula aktywnych klientów w symulacji; wpływa na to, jak często ten sam klient pojawia się ponownie w strumieniu |
| `generator.anomaly.probability` | `0.03` | 3 na każde 100 zdarzeń niesie cechy charakterystyczne dla fraudu — wysoka wartość koszyka, wiele pozycji, podwyższony wynik ryzyka |
| `generator.disorder.max.ms` | `30000` | Maksymalne rozproszenie czasowe zdarzeń w temacie Kafka — dane wysyłane są natychmiast, lecz wskutek warunków sieciowych mogą docierać w kolejności innej niż chronologiczna, z odchyleniem do 30 sekund względem czasu pomiaru |

### Poziom 1 — bieżący obraz sprzedaży produktu (okno 1-minutowe)

W krótkim oknie czasowym (1 minuta) chcemy wiedzieć, co dzieje się z każdym produktem w rozbiciu na poszczególnych klientów. Dla każdej unikalnej pary produkt–klient wyznaczamy cztery miary:

1. **Łączna wartość potwierdzonych sprzedaży** — suma wartości pozycji koszyka dla tego produktu i tego klienta, uwzględniająca wyłącznie zamówienia z potwierdzonym rozliczeniem płatności (`PAYMENT_OK`); mierzy przychód, który faktycznie wpłynął do sklepu — nie wartość złożonych koszyków ani zamówień odrzuconych.
2. **Liczba potwierdzonych transakcji** — ile razy dany klient kupił ten produkt w zdarzeniach `PAYMENT_OK`; pozwala odróżnić produkty drogie i rzadko kupowane od tanich i masowych, bez zanieczyszczenia zwrotami i nieudanymi płatnościami.
3. **Łączna liczba sprzedanych sztuk** — suma ilości (`quantity`) ze wszystkich pozycji koszyka zdarzeń `PAYMENT_OK` dla tej pary produkt–klient; sygnalizuje nagłe wzrosty popytu lub podejrzane zamówienia hurtowe.
4. **Liczba nieudanych płatności** — ile razy dla tej pary produkt–klient odnotowano zdarzenie `PAYMENT_FAILED` w oknie; surowy licznik, który na wyższym poziomie analizy pozwoli wyliczyć udział odrzuceń w szerszym przekroju.

**Uwaga:** Uzyskany w ten sposób bieżący obraz dla par produkt-klient powinien być możliwy:
* do dalszego przetwarzania 
* do obserwacji za pomocą narzędzi zewnętrznych (najnowsze dane, dla każdej pary produkt-klient)

### Wzbogacenie kontekstem słownikowym

Wyniki pierwszego poziomu wzbogacamy o informacje z katalogu produktów: nazwę handlową, kategorię, podkategorię, marżę procentową oraz historyczny wskaźnik ryzyka fraudu dla produktu. Dzięki temu te same liczby zyskują zupełnie inną wymowę — wysoka wartość sprzedaży laptopa (marża 18%, ryzyko fraudu 0,7) niesie inne implikacje biznesowe niż identyczna kwota ze sprzedaży książek (marża 35%, ryzyko fraudu 0,05). Wzbogacenie otwiera wymiar rentowności: zamiast samego przychodu możemy oszacować, który produkt faktycznie zarabia dla sklepu.

### Poziom 2 — obraz sprzedaży kategorii (okno 1-godzinne)

Na podstawie wzbogaconych wyników z poziomu 1 grupujemy dla każdej kolejnej godziny produkty według kategorii asortymentowej i wyznaczamy trzy miary dla każdej z nich:

1. **Szacowana marża wygenerowana przez kategorię** — iloczyn łącznej wartości sprzedaży i średniej marży produktów w kategorii; pozwala porównać, która część asortymentu faktycznie napędza zysk sklepu, a która jedynie generuje wolumen.
2. **Udział nieudanych płatności w kategorii** — stosunek sumy nieudanych prób płatności do sumy wszystkich prób rozliczenia (udanych i nieudanych) dla wszystkich produktów w kategorii; pozwala wskazać działy asortymentowe, które nieproporcjonalnie często przyciągają odrzucone transakcje i potencjalnie wymagają zwiększonej uwagi działu bezpieczeństwa.
3. **Przybliżona liczba unikalnych kupujących w kategorii** — ilu różnych klientów dokonało zakupu w danej kategorii w oknie czasowym. Ponieważ wyniki poziomu 1 zachowują tożsamość klienta (`userId`), na poziomie kategorii dysponujemy zbiorem identyfikatorów kupujących ze wszystkich produktów wchodzących w jej skład. Klient kupujący wiele produktów z tej samej kategorii powinien być policzony tylko raz. Kluczowe wymaganie: wyniki tego poziomu muszą być zapisywane w formie pozwalającej na ich późniejsze łączenie w szersze przedziały czasowe — godzinowe lub dobowe — bez konieczności powrotu do historii pojedynczych transakcji.

Wyniki mają być dostępne na ujściu najszybciej jak to możliwe.

### Sygnał alarmowy — card testing (testowanie skradzionych kart)

**Czym jest zjawisko?**
*Card testing* to technika stosowana przez przestępców w posiadaniu skradzionych danych kart płatniczych. Polega na składaniu zamówień o rosnącej wartości — najpierw małe kwoty, by sprawdzić, czy karta jest aktywna, potem duże zakupy o wysokiej wartości rynkowej produktów łatwych do odsprzedania (elektronika, smartwatche). Charakterystycznym sygnałem jest jednoczesne wystąpienie kilku cech: wysoki wynik ryzyka, duża liczba pozycji w koszyku, wysokie ilości sztuk, i porzucona lub wielokrotnie powtarzana próba płatności.

**Co obserwujemy w strumieniu?**
Generator modeluje to zjawisko następująco: gdy zdarzenie zostaje oznaczone jako anomalia (z prawdopodobieństwem `generator.anomaly.probability = 0.03`), koszyk może zawierać do **8 pozycji** (wobec maksymalnie 3 w normalnym zamówieniu), każda pozycja z ilością do **10 sztuk** (wobec maksymalnie 2 normalnie), a `fraudScore` losowany jest z przedziału **0,70–1,00**. Flaga `isFraudSuspected` ustawiana jest automatycznie przy `fraudScore > 0,65`. Dodatkowo, dla takich zdarzeń istnieje 50% szans na `paymentStatus = FAILED` — odzwierciedlając serię nieudanych prób obciążenia karty.

**Kiedy bić na alarm?**
Za próg alarmowy przyjmujemy sytuację, w której w oknie czasowym 1 minuty dla tego samego `cardBin` (pierwsze 6 cyfr karty) pojawią się co najmniej **2 zdarzenia** z `isFraudSuspected = true` — każde dla różnego `userId`. Taki wzorzec sugeruje, że ta sama karta (lub pula kart z tego samego banku) jest systematycznie testowana z różnych kont użytkowników.

Sygnały alarmowe powinny być dostępne najszybciej jak to tylko możliwe dla narzędzi zewnętrznych.

# Uwagi ogólne dotyczące implementacji

* Należy zwrócić uwagę na dokonywanie poprawnych obliczeń
* Należy uwzględnić fakt pojawiania się zdarzeń nieuporządkowanych
* Dane słownikowe należy dostarczyć w sposób, aby była możliwa ich zmiana w trakcie przetwarzania
* Aplikacja musi być zabezpieczona przed awariami, tak aby mogła je samodzielnie obsługiwać, lub jeśli to nie możliwe, wznowienie jej działania nie prowadziło do utraty danych. Poziom dostarczanych gwarancji *exactly-once*.
* Ujście musi być trwałą składnicą uwzględniającą wymagane gwarancje oraz umożliwiającą dokonywanie analiz zebranych tam danych (np. agregacji na wyższych poziomach). 