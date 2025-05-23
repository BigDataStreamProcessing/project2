# Zestaw 1 – Netflix-Prize-Data

Pochodzenie danych to https://www.kaggle.com/usdot/flight-delays

Dane zawierają informacje lotach samolotów zebrane przez Biuro Statystyki Transportu wchodzące w skład Departamentu Transportu Stanów Zjednoczonych (DOT - Department of Transportation) za rok 2015.

## Zbiory danych

Wykorzystywane są dwa zbiory danych.

### Strumień danych
Plik `flights-2015.zip` to zbiór plików mających format `csv` i następujące pola:

- `airline` – symbol linii lotniczych  
- `flightNumber` – numer lotu  
- `tailNumber` – numer samolotu  
- `startAirport` – lotnisko początkowe – symbol *IATA*  
- `destAirport` – lotnisko docelowe – symbol *IATA*  
- `scheduledDepartureTime` – planowy czas wylotu  
- `scheduledDepartureDayOfWeek` – dzień tygodnia planowego wylotu  
- `scheduledFlightTime` – planowy czas lotu  
- `scheduledArrivalTime` – planowy czas przylotu  
- `departureTime` – rzeczywisty czas wylotu  
- `taxiOut` – czas kołowania na lotnisku początkowym (czas, który upłynął między odłączeniem się od bramki lotniska początkowego a startem)  
- `distance` – długość przelotu  
- `taxiIn` – czas kołowania na lotnisku docelowym (czas, jaki upłynął między wylądowaniem i przybyciem do bramki na lotnisku docelowym)  
  Całkowity czas przelotu to: `taxiOut` + czas lotu + `taxiIn`  
- `arrivalTime` – rzeczywisty czas przelotu  
- `diverted` – wskazanie, czy lotnisko docelowe jest inne niż planowe  
- `cancelled` – wskazanie, czy lot został odwołany  
- `cancellationReason` – powód odwołania lotu: `A` - Linia lotnicza / przewoźnik; `B` - Pogoda; `C` - krajowy system lotniczy; `D` - Bezpieczeństwo  
- `airSystemDelay` – czas opóźnienia wynikającego z krajowego systemu lotniczego  
- `securityDelay` – czas opóźnienia wynikającego z reguł bezpieczeństwa  
- `airlineDelay` – czas opóźnienia wynikającego z działań linii lotniczej (przewoźnika)  
- `lateAircraftDelay` – czas opóźnienia wynikającego z opóźnionego samolotu  
- `weatherDelay` – czas opóźnienia wynikającego z warunków pogodowych  
- `cancelationTime` – czas anulowania lotu  
- `orderColumn` – pomocnicza kolumna określająca czas powiadomienia o zdarzeniu  
- `infoType` – typ zdarzenia: `D` – wylot, `A` – przylot, `C` – anulowanie lotu  

*Uwaga. Podane czasy wylotu oraz przylotu są zgodne z czasem na lotniskach startowym i docelowym. Aby przykładowo wyliczyć czas lotu należy uwzględnić strefy czasowe lotnisk.*

Załóż, że dane mogą być **nieuporządkowane** (pod względem `orderColumn`) – mogą być opóźnione o **5 minut**. 

### Statyczny 
Plik `airports.csv` zawiera następujące pola:

- `Airport ID` – identyfikator lotniska  
- `Name` – nazwa lotniska  
- `City` – miasto  
- `Country` – nazwa kraju lotniska  
- `IATA` – 3-literowy symbol *IATA* lotniska  
- `ICAO` – symbol *ICAO* lotniska  
- `Latitude` – szerokość geograficzna lotniska  
- `Longitude` – długość geograficzna lotniska  
- `Altitude` – wysokość lotniska  
- `Timezone` – strefa czasowa (pozwala wyliczyć czas *UTC*)  
- `DST` – strefa czasu letniego, wartości: `E` (Europa), `A` (USA / Kanada), `S` (Ameryka Południowa), `O` (Australia), `Z` (Nowa Zelandia), `N` (brak) lub `U` (nieznany)  
- `TimezoneName` – nazwa strefy czasowej  
- `Type` – typ lotniska  
- `State` – stan  


## Charakter przetwarzania 

### ETL – obraz czasu rzeczywistego

Utrzymywanie agregacji na poziomie dnia i stanu. 

Wartości agregatów to:

- liczba wylotów
- suma opóźnień w wylotach (loty, które wylatują zbyt szybko nie kompensują opóźnień)
- liczba przylotów
- suma opóźnień w przylotach (loty, które przylatują zbyt szybko nie kompensują opóźnień)

### Wykrywanie "anomalii"

Załóżmy, że dla każdego lotniska ważne jest jaka liczba samolotów, która do niego zmierza i jest obecnie w trakcie lotu ma zamiar do niego dotrzeć w danym okresie czasu. W sytuacji gdy taka liczba samolotów jest zbyt duża, konieczne jest uruchomienie dodatkowych osób, które mogą wesprzeć kontrolerów na tym lotnisku. Istotne jest aby wiedzieć o tym fakcie co najmniej 30 minut wcześniej – tylko wówczas jest wystarczająco dużo czasu na reakcję.

Wykrywanie anomalii ma raportować dla każdego lotniska sytuację, w której ustalona liczba `N` samolotów, które są obecnie w trakcie lotu, planowo dotrze do tego lotniska w interwale czasu `D` rozpoczynającym się za `30` minut.

Program ma obsługiwać następujące parametry:

- `D` – długość okresu czasu wyrażoną w minutach
- `N` – liczba samolotów w trakcie lotu lecących na określone lotnisko docelowe

Wykrywanie anomalii ma być dokonywane co 10 minut.

Przykładowo, dla parametrów `D=60`, `N=30`, program co `10` minut będzie raportował te lotniska, do których w ciągu `60` minut licząc od za `30` minut od chwili bieżącej planowo dotrze co najmniej 30 samolotów.

Raportowane dane mają zawierać:

- analizowany okres - okno (start i stop)
- nazwę lotniska,
- kod *IATA* lotniska,
- miasto lotniska
- stan, w którym miasto lotniska się znajduje
- liczbę samolotów docierających do lotniska w podanym okresie
- liczbę wszystkich samolotów lecących do lotniska 

