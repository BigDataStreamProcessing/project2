# Zestaw 5 - Chicago-Bicycle-Sharing

Pochodzenie danych to:

https://data.cityofchicago.org/Transportation/Divvy-Bicycle-Stations/bbyy-e7gq<br>
https://www.kaggle.com/yingwurenjian/chicago-divvy-bicycle-sharing-data

Dane zawierają informacje o rozpoczęciu i zakończeniu przejazdów rowerami w sieci współdzielonych rowerów w Chicago z lat 2014-2017.

## Zbiory danych

Wykorzystywane są dwa zbiory danych.

### Strumień danych
Plik `bicycle_result.zip` to zbiór plików mających format `csv` i następujące pola:

- `trip_id` – identyfikator przejazdu
- `start_stop` – czy rozpoczęcie (`0`) czy zakończenie (`1`) przejazdu
- `event_time` – etykieta czasowa zdarzenia
- `station_id` – identyfikator stacji
- `trip_duration` – czas trwania przejazdu (`0` dla rozpoczęcia przejazdu)
- `user_type` – typ użytkownika
- `gender` – płeć użytkownika
- `week` – numer tygodnia
- `temperature`
- `events` – opis zjawiska pogodowego 

Załóż, że dane mogą być **nieuporządkowane** – mogą być opóźnione o **godzinę**. 

### Statyczny 
Plik `Divvy_Bicycle_Stations.csv` zawiera następujące pola:

- `ID` – identyfikator stacji
- `Station Name` – nazwa stacji
- `Total Docks` - Łączna liczba stanowisk na stacji. Każde stanowisko może pomieścić jeden rower.
- `Docks in Service` – liczba działających (dostępnych do użytkowania) stanowisk.
- `Status` - Stan stacji. Stacje „In Service” są dostępne do użytku.
- `Latitude` – Szerokość geograficzna
- `Longitude` – Długość geograficzna
- `Location` – nazwa lokalizacji

## Charakter przetwarzania 

### ETL – obraz czasu rzeczywistego

Utrzymywanie agregacji na poziomie dnia i stacji. 

Wartości agregatów to:

- liczba wyjazdów
- liczba przyjazdów
- średnia temperatura

### Wykrywanie "anomalii"

Dla każdej stacji znana jest liczba działających stanowisk. Nadmierna liczba zwrotów (nie skompensowana wypożyczeniami), lub nadmierna liczba wypożyczeń (nie skompensowana zwrotami), nazwijmy tę liczbę `N`, w określonym interwale czasu jest sygnałem, że obsługa stacji powinna interweniować. Wykrywanie "anomalii" ma polegać na wykrywaniu sytuacji, kiedy powyżej zdefiniowana liczba `N` w stosunku do liczby działających stanowisk w ramach stacji w zadanym interwale przekroczy podaną wartość.

Program ma obsługiwać następujące parametry:

- `D` – długość okresu czasu wyrażona w minutach
- `P` – minimalny stosunek liczby `N` do liczby działających stanowisk w ramach stacji

Wykrywanie anomalii ma być dokonywane co 10 minut.

Przykładowo, dla parametrów `D=60`, `P=50` program co `10` minut będzie raportował te stacje rowerowe, w których w ciągu ostatnich `60` minut liczba `N` w stosunku do wielkości stacji przekroczyła `50%`.

Raportowane dane mają zawierać

- analizowany okres - okno (start i stop)
- nazwę stacji
- liczbę zwrotów nieskompensowanych wypożyczeniami
- liczbę wypożyczeń nieskompensowanych zwrotami
- wielkość stacji
- analizowany stosunek (liczba N do wielkości stacji)

