# Otus Highload Social Network

## Требования

- JDK 25
- Docker

## Сборка и запуск

### Через Docker Compose 

```bash
# 1. Собрать и запустить все сервисы (PostgreSQL, приложение, мониторинг)
docker compose up --build -d

# 2. (опционально) Посмотреть логи приложения
docker compose logs -f app

# 3. Остановить
docker compose down
```

### Локальная разработка (приложение вне Docker)

```bash
# 1. Собрать проект
./gradlew build

# 2. Запустить PostgreSQL
docker compose up -d postgres

# 3. Запустить приложение
./gradlew bootRun
```

Приложение будет доступно на `http://localhost:8080`.

## API

| Метод | Путь | Auth | Описание |
|---|---|---|---|
| POST | `/login` | нет | Аутентификация: `{"id": "uuid", "password": "..."}` → `{"token": "uuid"}` |
| POST | `/user/register` | нет | Регистрация: `{"first_name": "...", "second_name": "...", "birthdate": "2000-01-01", "gender": "MALE", "interests": "...", "city": "...", "password": "..."}` → `{"user_id": "uuid"}` |
| POST | `/user/get/{id}` | да (Bearer token) | Получение анкеты |
| GET | `/user/search?first_name=...&last_name=...` | да (Bearer token) | Поиск анкет |

---

# ДЗ: Производительность индексов

## Методика

- **Данные**: 998 949 анкет (~1M), загрузка через нативный `COPY` (Liquibase customChange, ~5 секунд).
- **Нагрузка**: Apache JMeter, 20 секунд на уровень, одновременные запросы c = 1 / 10 / 100 / 1000.
- **Метрики**: Grafana + Prometheus, `http_server_requests_seconds{uri=~"/user/search.*"}`, percentile-histogram включён.
- **Стенд**: PostgreSQL 16 (Docker), Spring Boot 4.1, 10 ядер, Docker Desktop VM 12 GB.
- **Запрос приложения** (`UserRepository.search`): префиксный поиск с капитализацией, `ORDER BY id LIMIT 50`:
  ```sql
  SELECT id, first_name, second_name, birthdate, gender, interests, biography, city, password_hash
  FROM users
  WHERE first_name LIKE :firstName AND second_name LIKE :secondName
  ORDER BY id
  LIMIT :limit
  ```

## До индекса: latency



















*Latency: 1 одновременный запрос, 20 секунд — до индекса*

![Latency c=1](Latency 1 одновременный запрос 20 секунд.png)

*Latency: 10 одновременных запросов, 20 секунд — до индекса*

![Latency c=10](Latency 10 одновременный запрос 20 секунд.png)

*Latency: 100 одновременных запросов, 20 секунд — до индекса*

![Latency c=100](Latency 100 одновременный запрос 20 секунд.png)

*Latency: 1000 одновременных запросов, 20 секунд — до индекса*

![Latency c=1000](Latency 1000 одновременный запрос 20 секунд.png)

## До индекса: throughput

*Throughput: 1 одновременный запрос, 20 секунд — до индекса*

![Throughput c=1](Throughput 1 одновременный запрос 20 секунд.png)

*Throughput: 10 одновременных запросов, 20 секунд — до индекса*

![Throughput c=10](Throughput 10 одновременный запрос 20 секунд.png)

*Throughput: 100 одновременных запросов, 20 секунд — до индекса*

![Throughput c=100](Throughput 100 одновременный запрос 20 секунд.png)

*Throughput: 1000 одновременных запросов, 20 секунд — до индекса*

![Throughput c=1000](Throughput 1000 одновременный запрос 20 секунд.png)

## Проблема: план запроса до индекса

Поиск вырождался в полное сканирование таблицы через первичный ключ: индекс по PK отдаёт все строки подряд, а предикаты `LIKE` применяются фильтром к каждой строке — **40 109 строк отфильтрованы, запрос выполнялся ~203 мс**:

```
Limit  (cost=0.42..6213.42 rows=50 width=198) (actual time=10.215..202.560 rows=50 loops=1)
  ->  Index Scan using users_pkey on users  (cost=0.42..126621.31 rows=1019 width=198) (actual time=10.214..202.543 rows=50 loops=1)
        Filter: (((first_name)::text ~~ 'Алекс%'::text) AND ((second_name)::text ~~ 'Смирн%'::text))
        Rows Removed by Filter: 40109
Planning Time: 0.204 ms
Execution Time: 202.592 ms
```

При 10 соединениях Hikari потолок пула: `10 соединений × (1000 / 203 мс) ≈ 49 запросов/с` — и на нагрузке упиралось в ~500–550 rps на 100/1000 одновременных запросов.

## Запрос добавления индекса

`app/src/main/resources/db/changelog/changesets/004_create_search_index.xml`:

```sql
CREATE INDEX users_search_idx ON users (second_name COLLATE "C", first_name COLLATE "C");
```

## EXPLAIN после индекса

Итоговый индекс `users_search_idx` — btree `(second_name COLLATE "C", first_name COLLATE "C")`:

```
Limit  (cost=2232.23..2232.36 rows=50 width=198) (actual time=2.572..2.589 rows=50 loops=1)
  ->  Sort  (cost=2232.23..2234.87 rows=1054 width=198) (actual time=2.571..2.578 rows=50 loops=1)
        Sort Key: id
        Sort Method: top-N heapsort  Memory: 46kB
        ->  Index Scan using users_search_idx on users  (cost=0.42..2197.22 rows=1054 width=198)
              (actual time=0.041..1.986 rows=1001 loops=1)
              Index Cond: (((second_name)::text >= 'Смирн'::text)
                        AND ((second_name)::text < 'Смиро'::text)
                        AND ((first_name)::text >= 'Алекс'::text)
                        AND ((first_name)::text < 'Алект'::text))
              Filter: (((first_name)::text ~~ 'Алекс%'::text)
                    AND ((second_name)::text ~~ 'Смирн%'::text))
Planning Time: 0.419 ms
Execution Time: 2.626 ms
```

Планер превратил `LIKE 'Смирн%'` в range-кондицию `>= 'Смирн' AND < 'Смиро'` и по индексу прочитал ровно **1 001 подходящую строку** вместо 40 109 строк с фильтром. Исполнение: **2.6 мс** против **202.6 мс** (×78).

## Перепроверка на живой таблице (шаги бенчмарка)

Все запросы выполнены на живой таблице `users` (~999k строк), запрос приложения как в проде. После каждого шага индекс удалялся и создавался следующий кандидат.

#### Шаг 1. Без индекса (baseline)

```
Limit  (cost=0.42..6213.42 rows=50 width=198) (actual time=10.215..202.560 rows=50 loops=1)
  ->  Index Scan using users_pkey on users  (cost=0.42..126621.31 rows=1019 width=198) (actual time=10.214..202.543 rows=50 loops=1)
        Filter: (((first_name)::text ~~ 'Алекс%'::text) AND ((second_name)::text ~~ 'Смирн%'::text))
        Rows Removed by Filter: 40109
Planning Time: 0.204 ms
Execution Time: 202.592 ms
```

#### Шаг 2. Кандидат A: plain btree `(second_name, first_name)` — индекс НЕ использован

```sql
CREATE INDEX users_search_idx ON users (second_name, first_name);
```

```
Limit  (cost=0.42..6574.57 rows=50 width=198) (actual time=14.633..119.829 rows=50 loops=1)
  ->  Index Scan using users_pkey on users  (cost=0.42..126618.45 rows=963 width=198) (actual time=14.632..119.815 rows=50 loops=1)
        Filter: (((first_name)::text ~~ 'Алекс%'::text) AND ((second_name)::text ~~ 'Смирн%'::text))
        Rows Removed by Filter: 40109
Planning Time: 0.334 ms
Execution Time: 119.867 ms
```

План не изменился — планер игнорирует plain btree для `LIKE`.

#### Шаг 3. Кандидат B: btree `COLLATE "C"` — итоговый

```sql
CREATE INDEX users_search_idx ON users (second_name COLLATE "C", first_name COLLATE "C");
```

```
Limit  (cost=2232.23..2232.36 rows=50 width=198) (actual time=2.572..2.589 rows=50 loops=1)
  ->  Sort  (cost=2232.23..2234.87 rows=1054 width=198) (actual time=2.571..2.578 rows=50 loops=1)
        Sort Key: id
        Sort Method: top-N heapsort  Memory: 46kB
        ->  Index Scan using users_search_idx on users  (cost=0.42..2197.22 rows=1054 width=198)
              (actual time=0.041..1.986 rows=1001 loops=1)
              Index Cond: (((second_name)::text >= 'Смирн'::text)
                        AND ((second_name)::text < 'Смиро'::text)
                        AND ((first_name)::text >= 'Алекс'::text)
                        AND ((first_name)::text < 'Алект'::text))
              Filter: (((first_name)::text ~~ 'Алекс%'::text)
                    AND ((second_name)::text ~~ 'Смирн%'::text))
Planning Time: 0.419 ms
Execution Time: 2.626 ms
```

#### Шаг 4. Кандидат C: btree `text_pattern_ops`

```sql
CREATE INDEX users_search_idx ON users (second_name text_pattern_ops, first_name text_pattern_ops);
```

```
Limit  (cost=3243.33..3243.45 rows=50 width=198) (actual time=3.535..3.544 rows=50 loops=1)
  ->  Sort  (cost=3243.33..3245.60 rows=911 width=198) (actual time=3.534..3.537 rows=50 loops=1)
        Sort Key: id
        Sort Method: top-N heapsort  Memory: 45kB
        ->  Bitmap Heap Scan on users  (cost=170.01..3213.06 rows=911 width=198) (actual time=2.206..3.287 rows=1001 loops=1)
              Recheck Cond: (((second_name)::text ~~ 'Смирн%'::text) AND ((first_name)::text ~~ 'Алекс%'::text))
              Heap Blocks: exact=335
              ->  Bitmap Index Scan on users_search_idx  (cost=0.00..169.78 rows=911 width=0) (actual time=2.153..2.153 rows=1001 loops=1)
                    Index Cond: (((second_name)::text ~~ 'Смирн%'::text) AND ((first_name)::text ~~ 'Алекс%'::text))
Planning Time: 0.231 ms
Execution Time: 3.596 ms
```

#### Шаг 5. Кандидат D: GIN `pg_trgm`

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX users_search_idx ON users USING gin (second_name gin_trgm_ops, first_name gin_trgm_ops);
```

```
Limit  (cost=3243.33..3243.45 rows=50 width=198) (actual time=2.941..2.952 rows=50 loops=1)
  ->  Sort  (cost=3243.33..3245.60 rows=911 width=198) (actual time=2.940..2.944 rows=50 loops=1)
        Sort Key: id
        Sort Method: top-N heapsort  Memory: 45kB
        ->  Bitmap Heap Scan on users  (cost=170.01..3213.06 rows=911 width=198) (actual time=1.953..2.750 rows=1001 loops=1)
              Recheck Cond: (((second_name)::text ~~ 'Смирн%'::text) AND ((first_name)::text ~~ 'Алекс%'::text))
              Heap Blocks: exact=335
              ->  Bitmap Index Scan on users_search_idx  (cost=0.00..169.78 rows=911 width=0) (actual time=1.897..1.897 rows=1001 loops=1)
                    Index Cond: (((second_name)::text ~~ 'Смирн%'::text) AND ((first_name)::text ~~ 'Алекс%'::text))
Planning Time: 0.222 ms
Execution Time: 2.986 ms
```

Итоговое состояние БД: `users_search_idx` — btree `(second_name COLLATE "C", first_name COLLATE "C")`; расширение `pg_trgm` осталось установленным (при желании откатывается `DROP EXTENSION pg_trgm`).

## Почему индекс именно такой


**`COLLATE "C"` — ключевое решение.** Коллация БД `en_US.utf8` (словарная) — в ней PostgreSQL не может выполнить range-скан по префиксу, потому что упорядочивание строк определяется правилами словаря. Индекс с `COLLATE "C"` хранит строки в байтовом порядке — только тогда `LIKE 'prefix%'` превращается в диапазон `[prefix, prefix+1)` и используется индексом.

| Кандидат |  Время выполнения (live) |
|---|---|
| без индекса (baseline) | 202.6 мс |
| A: plain btree `(second_name, first_name)` | 119.9 мс |
| **B: btree `(second_name COLLATE "C", first_name COLLATE "C")`** | **2.6 мс** |
| C: btree `(second_name text_pattern_ops, ...)` | 3.6 мс |
| D: GIN `pg_trgm` |  3.0 мс |

   - **A** подтвердил теорию: при словарной коллации plain btree для `LIKE` бесполезен — планер его даже не рассматривал (план остался PK-сканированием).
   - **B и D** на живой таблице почти равны (2.6 vs 3.0 мс), но у D два минуса: требуется расширение `pg_trgm` и дорогая bitmap-фаза с recheck; сам индекс обслуживает только `LIKE`/`~`.
   - **C** медленнее всех (3.6 мс) и, как и D, не ускоряет `=`, `<`, `>` — только операторы паттернов.
   - **B** — единственный, кто даёт прямой Index Scan по диапазону: не только самый быстрый, но и универсальный (заодно ускоряет равенство, диапазоны и сортировку).

4. **Выбор: B** — самый быстрый и универсальный. Запрос приложения приведён в соответствие с индексом (`ILIKE` → `LIKE` с капитализированным префиксом — семантика анкет в датасете хранится с заглавной буквы).

## После индекса: latency

*Latency: 1 одновременный запрос, 20 секунд — после индекса*

![Index Latency c=1](Index Latency 1 одновременный запрос 20 секунд.png)

*Latency: 10 одновременных запросов, 20 секунд — после индекса*

![Index Latency c=10](Index Latency 10 одновременный запрос 20 секунд.png)

*Latency: 100 одновременных запросов, 20 секунд — после индекса*

![Index Latency c=100](Index Latency 100 одновременный запрос 20 секунд.png)

*Latency: 1000 одновременных запросов, 20 секунд — после индекса*

![Index Latency c=1000](Index Latency 1000 одновременный запрос 20 секунд.png)

## После индекса: throughput

*Throughput: 1 одновременный запрос, 20 секунд — после индекса*

![Index Throughput c=1](Index Throughput 1 одновременный запрос 20 секунд.png)

*Throughput: 10 одновременных запросов, 20 секунд — после индекса*

![Index Throughput c=10](Index Throughput 10 одновременный запрос 20 секунд.png)

*Throughput: 100 одновременных запросов, 20 секунд — после индекса*

![Index Throughput c=100](Index Throughput 100 одновременный запрос 20 секунд.png)

*Throughput: 1000 одновременных запросов, 20 секунд — после индекса*

![Index Throughput c=1000](Index Throughput 1000 одновременный запрос 20 секунд.png)

## Тюнинг пула подключений (c=1000, 60 секунд)

Дополнительно расширен HikariCP: pool 10 → 25, `connection-timeout` 5 с, PgJDBC prepared-statement cache (512 запросов / 10 MiB), JVM `-XX:MaxRAMPercentage=60`.

*Latency: 1000 одновременных запросов, 60 секунд — после индекса + пул 25 (Hikari 25, timeout 5s)*

![ConnectionPool Index Latency c=1000](ConnectionPool Index Latency 1000 одновременный запрос 60 секунд.png)

*Throughput: 1000 одновременных запросов, 60 секунд — после индекса + пул 25 (Hikari 25, timeout 5s)*

![ConnectionPool Index Throughput c=1000](ConnectionPool Index Throughput 1000 одновременный запрос 60 секунд.png)

## Сводная таблица до/после

| Уровень | До индекса (pool 10) | После индекса (pool 10) | После индекса + pool 25 |
|---|---|---|---|
| | RPS / p50 / p95 / p99 | RPS / p50 / p95 / p99 | RPS / p50 / p95 / p99 |
| c=1 | 55 / 14 / 21 / 25 мс | 452 / 1 / 2 / 3 мс | — |
| c=10 | 507 / 16 / 27 / 47 мс | 3 754 / 1 / 3 / 5 мс | — |
| c=100 | 518 / 175 / 432 / 647 мс | 5 188 / 8 / 54 / 88 мс | — |
| c=1000 | 552 / 331 / 980 / 1390 мс | 6 186 / 4 / 155 / 268 мс | **7 253 / 4 / 119 / 226 мс** |

**Итог**: индекс убрал лимит соединений БД — пропускная способность выросла **в 8–11 раз** на всех уровнях (до ~7 250 rps), медианная латентность упала **на порядок** (до 1–8 мс), p99 на максимальной нагрузке сократился **в 5–6 раз** (с 1 390 до 226 мс).
