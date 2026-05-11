# Kryteria oceny projektu 2 — Przetwarzanie strumieni danych

## Informacje ogólne

Za projekt można uzyskać łącznie **50 punktów**. Projekt podzielony jest na **6 partii** oddawanych
i ocenianych **sekwencyjnie** — wykonanie i ocena kolejnych partii są możliwe dopiero po wykonaniu poprzednich.

Każda partia oceniana jest w skali **100% / 50% / 0%** punktów przypisanych danej partii:

| Wynik | Znaczenie |
|---|---|
| **100%** | Wszystkie wymagania spełnione poprawnie |
| **50%** | Główna funkcjonalność działa, lecz brakuje istotnych elementów lub występują poważne braki jakościowe |
| **0%** | Partia nie została złożona lub implementacja jest fundamentalnie błędna / nie działa |

### Terminy oddawania partii

* Każda partia ma wyznaczoną sesję zajęć jako termin oddania (S<sub>n</sub>). </br>
* Oddanie na S<sub>n</sub> oceniane jest normalnie (0% / 50% / 100%).</br>
* Oddanie na S<sub>n+1</sub> skutkuje obniżeniem uzyskanej oceny o jeden poziom (100%→50%, 50%→0%). 
* Oddanie na S<sub>n+2</sub> lub później nie jest punktowane. 
* We wszystkich przypadkach złożenie poprawnie zaimplementowanej partii — niezależnie od uzyskanej oceny — jest wymagane jako warunek przystąpienia do kolejnej. Przykładowo: student, który oddał partię terminowo i otrzymał 50%, zobowiązany jest do dostarczenia poprawionej wersji na S<sub>n+x</sub> mimo że ocena pozostaje niezmieniona.
* Istnieje możliwość oddania partii przed terminem, najwcześniej do S<sub>n-1</sub>. Pozwala to na oddanie innej jednej partii na sesji spóźnionej bez konsekwencji. 

### Forma złożenia

Każda partia składana jest przez system Moodle jako zadanie, najpóźniej na 24 godziny przed wyznaczoną sesją zajęć S<sub>n</sub>. Zgłoszenie zawiera dwa pliki:

* `partia-N.zip` — projekt w PyCharm lub IntelliJ gotowy do otwarcia,
* `partia-N.pdf` — wyjaśnienia dotyczące kluczowych decyzji projektowych zgodnych z listą zamieszczoną w treści zadania w Moodle (lista jest osobna dla każdej partii). Wyjaśnienia powinny być zwięzłe, merytoryczne, zawierać odwołania do precyzyjnie wskazanych fragmentów kodu (jeśli dotyczy) i odnosić się do wszystkich punktów z listy w Moodle. Wielkość maksymalna 1-2 strony.

Podczas sesji S<sub>n</sub> prowadzący typuje przed zajęciami wybranych studentów, którzy demonstrują działanie swojej implementacji. Pozostali studenci powinni być przygotowani do demonstracji — typowanie nie jest ogłaszane z wyprzedzeniem.

### Feedback i możliwość konsultacji

Każde z zadań służące do rejestracji efektów z poszczególnych partii będzie oceniane zgodnie z zasadami opisanymi poniżej. W przypadkach niepełnej punktacji dostępne stosowne komentarze. 

Przed każdą sesją będą dostępne dodatkowe konsultacje, na których będzie można wyjaśnić indywidualne kwestie. Ponadto zachęcam do zadawania pytań na forum oraz w szczególności odpowiadania i dyskutowania. Wspólne dzielenie się wiedzą dotyczącą rozwiązywania poszczególnych kwestii technicznych/implementacyjnych, jest mile widziane i może być uznane za dodatkową aktywność na zajęciach.

---

## Podsumowanie partii

| # | Zawartość                           | Pkt | Czas (est.) | Dokąd trafiają wyniki               |
|---|-------------------------------------|---|---|-------------------------------------|
| 1 | Infrastruktura + słownik dynamiczny | 8 | 4–5 h | konsola lub roboczy temat Kafka     |
| 2 | Poziom 1 obliczeń                   | 10 | 7–10 h | pośredni temat Kafka                |
| 3 | Konfiguracja ujścia                 | 7 | 3–5 h | weryfikacja struktury (brak danych) |
| 4 | Wzbogacenie                         | 2 | 2–3 h | konsola lub roboczy temat Kafka     |
| 5 | Poziom 2 obliczeń                   | 15 | 7–10 h | finalne ujście                      |
| 6 | Sygnały alarmowe                    | 8 | 4–6 h | finalne ujście                      |
| **Razem** |                                     | **50** | **27–39 h** |                                     |

---

## Partia 1 — Infrastruktura i słownik dynamiczny (8 pkt)

### Zakres

Fundament całego projektu. Weryfikowane jest tu rozumienie modelu gwarancji dostarczania oraz
poprawność konfiguracji odporności na awarie. Błędy na tym etapie propagują się na wszystkie kolejne
partie — dlatego partia jest oceniana rygorystycznie.

**Na tym etapie jedynym wymaganym wyjściem jest konsola lub temat roboczy Kafki** — aplikacja pozwala zaobserwować odczytywane zdarzenia.

### Kryteria — 100% (8 pkt)

- Aplikacja startuje i konsumuje ze skonfigurowanego tematu Kafka dla wybranego modułu.
- Aplikacja zabezpieczona przed awarią — w przypadku restartu utrata postępu przetwarzania nie przekracza 1 minuty.
- Dane słownikowe ładowane **dynamicznie** — zmiana danych źródłowych jest
  wykrywana przez działającą aplikację bez konieczności jej restartu.
- Wszystkie parametry konfiguracyjne obsługiwane z pliku `application.properties`, parametrów wywołania lub
  zmiennych środowiskowych; brak wartości zakodowanych na stałe w kodzie.

### Kryteria — 50% (4 pkt)

Aplikacja startuje i poprawnie konsumuje z Kafki **oraz** zachodzi co najmniej jeden z poniższych
braków:

- Słownik jest statyczny — brak mechanizmu wykrywania zmian bez restartu, lub
- Aplikacja nie jest zabezpieczona przed awarią, lub
- "Parametry" są zapisane na stałe w kodzie (przykładowo: temat źródłowy, miejsce zabezpieczające aplikację itp.) 

### Kryteria — 0%

Aplikacja nie startuje, nie łączy się z Kafką, lub nie dostarcza odczytanych zawartości na konsolę (lub do tematu roboczego Kafki).

---

## Partia 2 — Poziom 1 obliczeń (10 pkt)

### Zakres

Rdzeń analityczny projektu. Weryfikowana jest poprawność przetwarzania w czasie zdarzenia (*event
time*), obsługa zdarzeń nieuporządkowanych oraz kompletność i poprawność wszystkich miar
zdefiniowanych w sekcji „Poziom 1" dokumentacji modułu.

### Dokąd trafiają wyniki

Wyniki okien minutowych zapisywane są na **pośredni temat Kafka** (np. `<moduł>-poziom1`).
Temat ten może służyć (nie musi) jako źródło dla obliczeń Poziomu 2 oraz pozwala na obserwację za pomocą narzędzi zewnętrznych. Dla każdego klucza grupowania widoczny jest najnowszy wynik.

### Kryteria — 100% (10 pkt)

- Przetwarzanie oparte na **czasie zdarzenia** (*event time*) z uwzględnieniem nieuporządkowania zdarzeń.
- Wykorzystanie poprawnego okna o poprawnej długości z poprawnym kluczem grupowania.
- Wszystkie miary (3–4 w zależności od modułu) obliczone poprawnie zgodnie ze specyfikacją:
  właściwe pola, właściwe filtry typów zdarzeń, właściwe operacje agregujące.
- Wyniki zapisane na pośredni temat Kafka z gwarancją *at-least-once* (dokładność *exactly-once*
  wymagana jest dopiero na finalnym ujściu).
- Najnowszy wynik dla każdego klucza dostępny do odczytu przez narzędzia zewnętrzne.

### Kryteria — 50% (5 pkt)

Zostały użyte poprawne okna i one działają, co najmniej połowa miar jest obliczona poprawnie **oraz** zachodzi co najmniej
jeden z poniższych braków:

- Brak obsługi zdarzeń nieuporządkowanych, lub
- Co najmniej jedna miara brakująca albo obliczona z istotnym błędem, lub
- Niepoprawny klucz grupowania, lub
- Wyniki nie są zapisywane do tematu Kafki.

### Kryteria — 0%

Nie zostały użyte poprawne okna lub przetwarzanie nie działa, lub większość miar została wyliczona niepoprawnie.  

---

## Partia 3 — Konfiguracja ujścia (7 pkt)

### Zakres

Przygotowanie finalnego ujścia danych, na które trafią wyniki Poziomu 2. Partia oceniana jest strukturalnie — weryfikowana jest poprawność konfiguracji
i schematu, nie przepływ danych.

### Dokąd trafiają wyniki

Na tym etapie żadne dane nie są jeszcze zapisywane do ujścia. Weryfikowana jest wyłącznie
poprawność jego struktury (tabele, schemat, konfiguracja złącza (*konektora*)).

### Kryteria — 100% (7 pkt)

- Wybrana technologia ujścia została uzasadniona zgodnie ze stanem faktycznym (dokumentacją) — uzasadnienie powinno odwoływać się do wymagań dotyczących
  gwarancji, możliwości agregacji i łączenia wyników między kolejnymi dobami/sesjami.
- Zdefiniowane tabele/schematy dla wyników Poziomu 2.
- Connector Spark / Kafka Connect poprawnie skonfigurowany (zależności, parametry połączenia).
- Gwarancje *exactly-once* po stronie zapisu skonfigurowane (np. idempotentny zapis, transakcje).
- Schemat wyników Poziomu 2 umożliwia **łączenie wyników z kolejnych dób/sesji bez powrotu do danych źródłowych**.

### Kryteria — 50% (4 pkt)

Ujście wybrane, tabele utworzone i connector skonfigurowany **oraz** zachodzi co najmniej jeden z poniższych braków:

- Gwarancje *exactly-once* po stronie zapisu nie zostały skonfigurowane, lub
- Schemat nie umożliwia dalszych agregacji — łączenia wyników między dobami/sesjami.

### Kryteria — 0%

Brak ujścia, brak tabel lub connector nie został przygotowany.

---

## Partia 4 — Wzbogacenie (2 pkt)

### Zakres

Połączenie wyników Poziomu 1 z danymi słownikowymi. Partia jest niewielka punktowo, ponieważ to operacja technicznie prosta — jednak jej poprawność jest warunkiem koniecznym dla uzyskania ocen z kolejnych partii.

### Dokąd trafiają wyniki

Wzbogacony strumień na tym etapie trafia na **konsolę** lub **roboczy temat Kafka**
(np. `<moduł>-wzbogacony-dev`). Finalne podłączenie do ujścia następuje w Partii 5. Po implementacji Partii 5 powyższe tymczasowe ujście nie powinno już być używane.

### Kryteria — 100% (2 pkt)

- Wzbogacanie wyników Poziomu 1 o dane słownikowe obejmuje wszystkie pola słownikowe wskazane w sekcji „Wzbogacenie kontekstem słownikowym" dokumentacji modułu.
- Obsłużony przypadek braku wpisu w słowniku dla danego klucza (np. wartości domyślne, filtrowanie z ostrzeżeniem w logu — nie cichy NullPointerException).
- Aktualizacje słownika podczas pracy aplikacji są odzwierciedlane w nowo przetwarzanych zdarzeniach (mechanizm z Partii 1 działa end-to-end).

### Kryteria — 50% (1 pkt)

Wzbogacanie wyników Poziomu 1 o dane słownikowe działa dla typowego przypadku, ale brakuje obsługi brakujących wpisów słownikowych **lub**
aktualizacje słownika nie są propagowane do strumienia wzbogaconego.

### Kryteria — 0%

Wzbogacanie wyników Poziomu 1 o dane słownikowe nie zostało zaimplementowane lub pola wzbogacenia są błędne.

---

## Partia 5 — Poziom 2: agregacje narastające (15 pkt)

### Zakres

Najbardziej wymagająca partia. Agregacje narastające budowane są **wyłącznie na wynikach Poziomu 1** — nie wolno wracać do surowego strumienia. Przy rosnącej liczbie kluczy w zależności od typu obliczeń ich dokładne implementacje wymagają proporcjonalnie rosnących zasobów — konieczne jest w takich przypadkach stosowanie metod przybliżonych dających odpowiedź z kontrolowanym błędem przy stałym zużyciu pamięci. Wyniki muszą być zapisywane w formie pozwalającej
na ich łączenie z wynikami kolejnych dób/sesji bez powrotu do danych źródłowych.

### Dokąd trafiają wyniki

Wyniki zapisywane są do **finalnego ujścia** skonfigurowanego w Partii 3, z gwarancjami
*exactly-once*. Ujście musi umożliwiać wykonywanie agregacji na zebranych danych
(np. sumowanie wyników z wielu dni bez powrotu do Kafki).

### Kryteria — 100% (15 pkt)

- Poziom 2 zbudowany **wyłącznie** na strumieniu wzbogaconych wyników Poziomu 1 — brak odwołań do surowego strumienia zdarzeń.
- Poprawny klucz grupowania.
- **Miara 1** i **Miara 2** obliczone poprawnie: narastające sumy, narastające średnie ważone lub
  narastające proporcje — zgodnie ze specyfikacją modułu.
- **Miara 3** zaimplementowana za pomocą algorytmu przybliżonego przy stałym zużyciu pamięci i z kontrolowanym błędem.
- **Miara 3** zapisana w ujściu w sposób umożliwiający scalanie wyników kolejnych dób bez powrotu do danych źródłowych.
- Wyniki aktualizowane przy każdym napływającym oknie z Poziomu 1 (tryb narastający, aktualizujący, nie batch dobowy).
- Zapis do finalnego ujścia z gwarancjami *exactly-once*.

### Kryteria — 50% (7–8 pkt)

Miara 1 i Miara 2 obliczone poprawnie na strumieniu wzbogaconych wyników Poziomu 1 **oraz** zachodzi co najmniej jeden z poniższych braków:

- Miara 3 obliczana dokładnie (brak algorytmu przybliżonego — np. `COUNT(DISTINCT ...)` na pełnym
  zbiorze lub dokładna mediana przez sortowanie), lub
- Stan szkicu nie jest serializowany — zapisywany jest tylko wynik końcowy, uniemożliwiając późniejsze łączenie, lub
- Poziom 2 częściowo lub całkowicie zbudowany na surowym strumieniu zamiast na wynikach Poziomu 1.

### Kryteria — 0%

Dowolna z miar 1 i 2 obliczona błędnie lub Poziom 2 obliczany na surowym strumieniu, a nie wyniku Poziomu 1 lub brak implementacji.

---

## Partia 6 — Sygnały alarmowe (8 pkt)

### Zakres

Każdy moduł definiuje dwa rodzaje alarmów: **alarm licznikowy** (wzorzec w oknie czasowym) oraz
**alarm natychmiastowy** (pojedyncze zdarzenie spełniające warunek). Kluczowe znaczenie ma tu
opóźnienie dostarczenia alarmu — dla obu typów obowiązują różne oczekiwane czasy reakcji.

### Dokąd trafiają wyniki

Student samodzielnie wybiera miejsce docelowe dla sygnałów alarmowych. Wymagania niezależne od wyboru technologii: alarmy muszą być dostępne natychmiast po wyzwoleniu oraz dostępne dla narzędzi zewnętrznych.

### Czasy dostarczenia alarmów

**Alarm natychmiastowy** — emitowany bez zwłoki, niezwłocznie po wystąpieniu zdarzenia
spełniającego warunek. Maksymalne akceptowalne opóźnienie wynika tylko i wyłącznie z charakteru użytego narzędzia (np. czasu obsługi mikro-wsadu lub częstotliwości wykonywania punktów kontrolnych).

**Alarm licznikowy** — emitowany po potwierdzeniu wystąpienia wzorca w oknie czasowym. Ze względu
na konieczność obserwacji sekwencji zdarzeń w czasie, maksymalne akceptowalne opóźnienie
wynika tylko i wyłącznie z charakteru użytego narzędzia (np. czasu obsługi mikro-wsadu lub częstotliwości wykonywania punktów kontrolnych), typu okna czasowego oraz konieczności uwzględnienia nieuporządkowania zdarzeń.

### Kryteria — 100% (8 pkt)

- **Alarm licznikowy** poprawnie zaimplementowany:
  - poprawny typ i parametry okna,
  - poprawny klucz grupowania,
  - poprawne warunki progowe i filtry zdarzeń zgodne ze specyfikacją,
  - opóźnienie maksymalne (patrz powyżej).
- **Alarm natychmiastowy** poprawnie zaimplementowany:
  - brak użycia zbędnych elementów w definicji przetwarzania,
  - emitowany bez czekania na zamknięcie jakiegokolwiek okna,
  - opóźnienie maksymalne (patrz powyżej).
- Rekordy alarmów zapisywane do wybranego miejsca docelowego, dostępnego natychmiast dla narzędzi zewnętrznych z wymaganymi polami: typ alarmu, klucz, znacznik czasu, wartości wyzwalające alarm itp.

### Kryteria — 50% (4 pkt)

Jeden z dwóch alarmów działa w pełni poprawnie **albo** oba działają, lecz zachodzi co najmniej jeden
z poniższych braków:

- Alarm natychmiastowy używa zbędnych elementów w definicji przetwarzania, co skutkuje zwiększonym opóźnieniem w dostarczaniu na ujście, lub
- Warunki progowe istotnie niezgodne ze specyfikacją (np. zły klucz grupowania, błędne pole), lub
- Alarmy dostępne tylko na konsoli — niedostępne dla narzędzi zewnętrznych.

### Kryteria — 0%

Żaden alarm nie działa w pełni poprawnie lub warunki są fundamentalnie niezgodne ze specyfikacją modułu.


