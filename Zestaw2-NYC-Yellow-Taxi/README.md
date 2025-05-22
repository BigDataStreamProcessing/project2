# Zestaw 2 - NYC-Yellow-Taxi

Pochodzenie danych to https://www1.nyc.gov/site/tlc/about/tlc-trip-record-data.page

Dane zawierają informacje o rozpoczęciu i zakończeniu kursów taksówek w Nowym Jorku w ostatnich dwóch miesiącach 2018 roku.

## Zbiory danych

Wykorzystywane są dwa zbiory danych.

### Strumień danych
Plik `yellow_tripdata_result.zip` to zbiór plików mających format `csv` i
następujące pola:

- `tripID` – identyfikator przejazdu
- `start_stop` – czy rozpoczęcie (`0`) czy zakończenie (`1`) przejazdu
- `timestamp` – etykieta czasowa
- `locationID` – identyfikator strefy taksówek
- `passenger_count` – liczba pasażerów
- `trip_distance` – długość przejazdu (`0` dla rozpoczęcia przejazdu)
- `payment_type` – typ płatności (`0` dla rozpoczęcia przejazdu)
- `amount` – opłata za przejazd (`0` dla rozpoczęcia przejazdu)
- `VendorID` – identyfikator korporacji 

Załóż, że dane mogą być **nieuporządkowane** – mogą być opóźnione o **jeden dzień**. 

### Statyczny 
Plik `taxi_zone_lookup.csv` zawiera następujące pola:

- `LocationID` – identyfikator strefy
- `Borough` – dzielnica
- `Zone` – nazwa strefy
- `service_zone` – nazwa strefy serwisowej 

## Charakter przetwarzania 

### ETL – obraz czasu rzeczywistego

Utrzymywanie agregacji na poziomie dnia i dzielnicy.

Wartości agregatów to:
- liczba wyjazdów
- liczba przyjazdów
- sumaryczna osób wyjeżdżających
- sumaryczna osób przyjeżdżających

### Wykrywanie "anomalii"

Wykrywanie "anomalii" ma polegać na wykrywaniu dużej różnicy w liczbie osób wyjeżdżających z danej dzielnicy w stosunku do liczby przyjeżdżających do danej dzielnicy w określonym czasie.

Program ma ma obsługiwać następujące parametry:

- `D` - długość okresu czasu wyrażoną w godzinach
- `L` - liczbę osób (minimalna)

Wykrywanie anomalii ma być dokonywane co godzinę.

Przykładowo, dla parametrów `D=4`, `L=10000` program co godzinę będzie raportował te dzielnice, w których w ciągu ostatnich `4` godzin liczba osób "zmniejszyła się" o co najmniej `10000` osób.

Raportowane dane mają zawierać:
- analizowany okres - okno (start i stop)
- nazwę dzielnicy
- liczbę osób wyjeżdżających
- liczbę osób przyjeżdżających
- różnicę w powyższych liczbach

