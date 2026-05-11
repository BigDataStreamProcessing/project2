# Generatory strumieni danych

Projekt Maven z 9 modułami generującymi realistyczne strumienie danych JSON.

## Struktura
```
Zestawy/
├── pom.xml                         ← parent POM (zarządzanie wersjami)
├── generator-prosumenci/           ← ⚡ Prosumenci energii (PV, tarify G11/G12/G12w)
├── generator-transport/            ← 🚌 Transport miejski (GPS, opóźnienia)
├── generator-pogoda/               ← 🌡 Stacje meteorologiczne IoT
├── generator-ecommerce/            ← 🛒 Transakcje e-commerce (fraud detection)
├── generator-wodociagi/            ← 💧 Sieć wodociągowa (wycieki, ciśnienie)
├── generator-parking/              ← 🅿 Smart parking (strefy, overstay)
├── generator-powietrze/            ← 🌫 Jakość powietrza / smog (AQI)
├── generator-maszyny/              ← 🏭 Industry 4.0 (OEE, predictive maintenance)
└── generator-gielda/               ← 🌾 Giełda towarowa (ticki, flash crash)
```

## Budowanie

Moduły są niezależne – każdy można zbudować i uruchomić osobno bez budowania pozostałych.

```bash
# Budowanie pojedynczego modułu (wymagane przed pierwszym uruchomieniem)
mvn install -pl generator-prosumenci -DskipTests
mvn install -pl generator-transport  -DskipTests
mvn install -pl generator-pogoda    -DskipTests
mvn install -pl generator-ecommerce -DskipTests
mvn install -pl generator-wodociagi -DskipTests
mvn install -pl generator-parking   -DskipTests
mvn install -pl generator-powietrze -DskipTests
mvn install -pl generator-maszyny   -DskipTests
mvn install -pl generator-gielda    -DskipTests

# Budowanie wszystkich modułów naraz
mvn install -DskipTests
```

> Budowanie jest wymagane przed pierwszym uruchomieniem `mvn exec:java`.

W wyniku budowania tworzone są dwa pliki jar
* `generator-*-1.0-SNAPSHOT.jar` — tzw. fat JAR
* `original-generator-*-1.0-SNAPSHOT.jar` — standardowy JAR bez zależności

## Uruchamianie

### Podgląd (bez Kafki)
```
mvn exec:java -pl generator-prosumenci -Dexec.args="" -Dpreview=true
mvn exec:java -pl generator-transport  -Dexec.args="" -Dpreview=true
mvn exec:java -pl generator-pogoda    -Dexec.args="" -Dpreview=true
mvn exec:java -pl generator-ecommerce -Dexec.args="" -Dpreview=true
mvn exec:java -pl generator-wodociagi -Dexec.args="" -Dpreview=true
mvn exec:java -pl generator-parking   -Dexec.args="" -Dpreview=true
mvn exec:java -pl generator-powietrze -Dexec.args="" -Dpreview=true
mvn exec:java -pl generator-maszyny   -Dexec.args="" -Dpreview=true
mvn exec:java -pl generator-gielda    -Dexec.args="" -Dpreview=true
```

### Z Kafką
Upewnij się, że Kafka działa na localhost:9092.
```
mvn exec:java -pl generator-prosumenci
mvn exec:java -pl generator-transport 
mvn exec:java -pl generator-pogoda
mvn exec:java -pl generator-ecommerce
mvn exec:java -pl generator-wodociagi
mvn exec:java -pl generator-parking
mvn exec:java -pl generator-powietrze
mvn exec:java -pl generator-maszyny
mvn exec:java -pl generator-gielda
```

### Budowanie fat-JARów
```
mvn clean package -DskipTests
# JAR w: generator-xxx/target/generator-xxx-1.0-SNAPSHOT.jar
```

### Uruchamianie na maszynie klastra - przykład
```bash
java -Dpreview=true -jar generator-xxx-1.0-SNAPSHOT.jar \
 --generator.interval.ms=500
```

## Topiki Kafki
| Moduł           | Temat Kafka              | Klucz rekordu  |
|-----------------|--------------------------|----------------|
| prosumenci      | prosumenci-odczyty        | prosumentId    |
| transport       | transport-pozycje         | vehicleId      |
| pogoda          | pogoda-odczyty            | stationId      |
| ecommerce       | ecommerce-transakcje      | userId         |
| wodociagi       | wodociagi-odczyty         | nodeId         |
| parking         | parking-zdarzenia         | zoneId         |
| powietrze       | powietrze-odczyty         | stationId      |
| maszyny         | maszyny-telemetria        | machineId      |
| gielda          | gielda-ticki              | contractId     |

## Konfiguracja anomalii
Każdy generator obsługuje parametr `generator.anomaly.probability` (0.0–1.0) </br>
w `application.properties`. Domyślnie 3–8% zdarzeń to anomalie (typ i opis w Javadoc klasy `*Event`).

