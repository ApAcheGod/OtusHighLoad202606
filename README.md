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

# 2. Запустить Citus-кластер (coordinator + 3 workers)
docker compose up -d citus-coordinator citus-worker-1 citus-worker-2 citus-worker-3 citus-init

# 3. Запустить приложение
./gradlew bootRun
```

Приложение будет доступно на `http://localhost:8080`.

## API

| Метод | Путь | Auth | Описание |
|---|---|---|---|
| POST | `/login` | нет | Аутентификация: `{"id": "uuid", "password": "..."}` → `{"token": "uuid"}` |
| POST | `/user/register` | нет | Регистрация: `{"first_name": "...", "second_name": "...", "birthdate": "2000-01-01", "gender": "MALE", "interests": "...", "city": "...", "password": "..."}` → `{"user_id": "uuid"}` |
| GET | `/user/get/{id}` | да (Bearer token) | Получение анкеты |
| GET | `/user/search?first_name=...&last_name=...` | да (Bearer token) | Поиск анкет |
| PUT | `/friend/set/{friendUserId}` | да (Bearer token) | Добавить друга (взаимно) |
| PUT | `/friend/delete/{friendUserId}` | да (Bearer token) | Удалить друга (взаимно) |
| POST | `/post/create` | да (Bearer token) | Создать пост: `{"text": "..."}` → `post_id` |
| GET | `/post/get/{id}` | да (Bearer token) | Получить пост |
| PUT | `/post/update` | да (Bearer token) | Обновить пост: `{"id": "uuid", "text": "..."}` |
| PUT | `/post/delete/{id}` | да (Bearer token) | Удалить пост |
| GET | `/post/feed?offset=0&limit=10` | да (Bearer token) | Лента друзей |
| POST | `/dialog/{userId}/send` | да (Bearer token) | Отправить сообщение: `{"text": "...", "client_message_id"?: "uuid"}` → `message_id` |
| GET | `/dialog/{userId}/list` | да (Bearer token) | История диалога: `[{"from","to","text"}]` (+ `id`, `created_at`); без параметров — весь диалог, `?offset=&limit=` опционально |

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

---

# ДЗ: Репликация

## Шаг 1. Baseline: нагрузочное тестирование чтения до репликации

### Методика

- **Запросы**: `GET /user/get/{id}` и `GET /user/search?first_name=Ал&last_name=См`.
- **Инструмент**: JMeter 5.6.3, план `user_reads.jmx`.
- **Уровни**: c = 1 / 10 / 100 / 1000 одновременных запросов; 20 сек/уровень (60 с для c=1000).
- **Стенд**: PostgreSQL 16 (Docker), Spring Boot 4.1, 10 ядер, VM 12 GB. База ~1M анкет, индекс `users_search_idx`.
- **Метрики**: RPS, p50/p95/p99 per label; суммарный RPS = search + get.

### Результаты (до репликации, единый PostgreSQL)

| Уровень | search RPS | get RPS | Суммарно | search p50/p95/p99 | get p50/p95/p99 | Ошибки |
|---|---|---|---|---|---|---|
| c=1 | 395 | 395 | 789 | 2 / 2 / 3 мс | 1 / 1 / 2 мс | 0 |
| c=10 | 2 367 | 2 367 | 4 733 | 2 / 4 / 7 мс | 1 / 3 / 5 мс | 0 |
| c=100 | 3 244 | 3 241 | 6 484 | 13 / 38 / 62 мс | 11 / 35 / 59 мс | 0 |
| c=1000 | 4 502 | 4 493 | 8 995 | 93 / 210 / 320 мс | 91 / 207 / 293 мс | 0 |

**Вывод (baseline)**: на одиночном PostgreSQL сервис держит **~9 000 rps** на чтении (суммарно по двум эндпоинтам) при c=1000 без единой ошибки; слейвы пока отсутствуют — вся нагрузка идёт на единственный инстанс. Эти цифры — база для сравнения после перевода чтения на реплики.

## Шаг 2. Инфраструктура: 1 мастер + 2 слейва (потоковая репликация)

### Топология

| Сервис | Порт | Роль | Данные |
|---|---|---|---|
| `postgres-master` | 5432 | primary (запись) | volume `pg_master_data` |
| `postgres-slave-1` | 5433 | standby (чтение) | volume `pg_slave1_data` |
| `postgres-slave-2` | 5434 | standby (чтение) | volume `pg_slave2_data` |
| `postgres_exporter` / `_slave1` / `_slave2` | 9187/9188/9189 | метрики PG | — |

### Конфигурация

- **Мастер**: `wal_level=replica`, `max_wal_senders=10`, `max_replication_slots=10`, `wal_keep_size=1024`, `hot_standby=on`; инициализация роли `replica` через `init.sql` и настройка HBA для localhost репликации.
- **Слейвы**: автоматическая инициализация через `pg_basebackup`, создание `standby.signal` и настройка `primary_conninfo`; старт как `hot standby`.
- **Приложение**: подключение к `postgres-master:5432`; Liquibase привязан к мастеру.
- **Мониторинг**: exporter на каждый узел (master, slave1, slave2).

### Проверка репликации

```
postgres-master: SELECT application_name, state, sync_state FROM pg_stat_replication;
  pg_slave_1 | streaming | async
  pg_slave_2 | streaming | async

postgres-slave-N: SELECT pg_is_in_recovery(), pg_last_wal_receive_lsn() = pg_last_wal_replay_lsn();
  t | t   (режим standby, WAL догнан)

count(*) FROM users: master = slave1 = slave2 = 998 949
```

Live-проверка: `POST /user/register` → строка появляется на обоих слейвах за <2 с. Репликация асинхронная (`sync_state=async`) — синхронный кворум включим позже для эксперимента с потерями.

## Шаг 3. Роутинг: чтение на слейвы, запись на мастер

### Реализация

- Кастомный `AbstractRoutingDataSource` (`ReplicationRoutingDataSource.kt`): определяет источник по флагу `isCurrentTransactionReadOnly`; слейв выбирается round-robin **парами** (`counter/2 % count`) — оба чтения (token-lookup + запрос) попадают на один узел, а запросы чередуются между слейвами.
- Three independent HikariCP-пула: `masterDataSource`, `slaveDataSource(url)`, и `clientDataSource` как `LazyConnectionDataSourceProxy` + `@Primary`. Прокси обязателен: флаг `readOnly` выставляется транзакцией позже, чем получена connection, без proxy роутер всегда выбирал бы мастер.
- `@Transactional(readOnly = true)` на всех читателях: `UserService.getById/search/validatePassword`, `TokenService.resolveUserId`, `UserRepository.count` / `SessionRepository.count`. Записывающие методы — обычный `@Transactional`.
- `application.yml`: `spring.datasource.*` удалён; Liquibase привязан к мастеру.

### Проверка роутинга (эмпирически, pg_stat_statements по узлам)

После `register → login → 50× (search + get)` и сброса `pg_stat_statements` на слейвах:

```
postgres-master:  INSERT INTO users / sessions               ← только записи
                  (SELECT COUNT(*): только Liquibase databasechangelog)

postgres-slave-1: SELECT ... FROM sessions WHERE token=$1    49
                  SELECT ... FROM users WHERE second_name LIKE... 47
postgres-slave-2: SELECT ... FROM sessions WHERE token=$1    51
                  SELECT ... FROM users WHERE second_name LIKE... 52
```

gauge-счётчики (после правки count()): slave-1 считает sessions, slave-2 — users;
на мастере счётчики SELECT COUNT(*) перестали расти.

Вывод: чтение не покидает слейвы (два слейва делят read-нагрузку поровну), на мастер попадают только INSERT/UPDATE/DELETE и Liquibase.

### Запуск в Docker

Окружение в `docker-compose.yml` для сервиса `app` использует имена-хостнеймы контейнеров (`postgres-master:5432`, `postgres-slave-1/2:5432`) — переменные `APP_DATASOURCE_*` и `SPRING_LIQUIBASE_*` без префикса `spring.` у кастомного свойства `app.datasource.*` (иначе Spring не свяжет env-переменные и приложение из контейнера будет ходить на `localhost:5432`). Healthcheck — TCP-проба через `/dev/tcp` (`curl` в образе `eclipse-temurin:25-jre` отсутствует).

## Шаг 4. Load test после репликации (чтение ушло на слейвы)

Тот же план JMeter (`user_reads.jmx`), те же уровни c=1/10/100/1000. Пул слейвов 25 (на c=1000 дополнительно проверены пулы 100 — результат не изменился, см. ниже), мастер — 25.

| Уровень | search RPS | get RPS | Суммарно | search p50/p95/p99 | get p50/p95/p99 | Ошибки |
|---|---|---|---|---|---|---|
| c=1 | 230 | 230 | 460 | 2 / 4 / 5 мс | 1 / 3 / 4 мс | 0 |
| c=10 | 1 903 | 1 903 | 3 806 | 3 / 4 / 6 мс | 2 / 3 / 4 мс | 0 |
| c=100 | 2 886 | 2 886 | 5 772 | 13 / 36 / 53 мс | 11 / 32 / 49 мс | 0 |
| c=1000 | 2 843 | 2 843 | 5 686 | 145 / 261 / 338 мс | 141 / 259 / 336 мс | 0 |

### Что показывает сравнение с baseline (Шаг 1)

- На малых уровнях (c=1..100) суммарный RPS ниже baseline на 10–40 %. Причина — накладные расходы маршрутизации: каждый запрос теперь выполняет **две** транзакции чтения (token-lookup + сам запрос) с BEGIN/COMMIT; при едином пуле в baseline те же два запроса шли с autocommit.
- На c=1000 суммарный RPS **ниже baseline (5 686 vs 8 921)**. Это при том, что каждый слейв отвечает за ~половину нагрузки. Анализ узкого места по метрикам во время прогона:
  - `hikaricp_connections_pending≈0`, `active≈49/100` — пулы подключений не исчерпаны;
  - CPU во время прогона: **app ≈ 450–500 %** (5 ядер), **postgres-slave-1 ≈ 300–500 %**, **postgres-slave-2 ≈ 300 %** → суммарно > 10 ядер Docker Desktop VM;
  - среднее время исполнения SQL на слейвах — 0.01–0.94 мс (по `pg_stat_statements`): сами запросы не медленные.
- Контрольный эксперимент: при отключённом роутинге на 2-й слейв (оба пула → один слейв, код через один и тот же app) — 4 136 rps при 100 % CPU узла (**слейв ~500 % + app ~500 %**). Т.е. один standby обслуживает ~4 тыс. rps, два — ~5.7 тыс., и в обоих случаях стенд упирается в CPU VM, а не в PostgreSQL.

**Вывод по нагруженному стенду**: репликация разнесла чтение по узлам (мастер разгружен — на нём только INSERT/UPDATE/прочее ~0 % CPU при read-нагрузке), но поскольку стенд является CPU-bound (Docker Desktop с 10 vCPU: JVM-приложение + 2 standby + мониторинг), суммарный потолок RPS на этом железе ниже, чем у одиночного сервера в baseline. На реальном стенде (отдельные хосты/машины) выигрыш будет проявляться по мере масштабирования read-нагрузки; здесь же зафиксирован предел стенда.

## Шаг 5. Эксперимент с потерями транзакций: async vs sync quorum

Методика: на мастере коммитим транзакции, затем `docker kill --signal=KILL` мастера (жесткий сбой без flush/stop) и смотрим, какие коммиты видны на слейвах — именно они были бы потеряны при failover (посмертное поднятие мастера для восстановления стенда к потерям не относится).

### Async (исходное состояние)

Стоп слейва + 3 коммита на мастере + убийство мастера → **3 потери из 3** при промоушене слейва (WAL остался только на упавшем мастере).

### Sync quorum (ANY 2)

`ALTER SYSTEM SET synchronous_standby_names = 'ANY 2 (pg_slave_1, pg_slave_2)'` + рестарт мастера. 3 коммита с аck обоим слейвам (latency 130–145 мс). Убийство мастера → **0 потерь**, оба слейва содержат все коммиты, любой можно промоутить.

### Поведение при недоступности кворума

Синхронная репликация «ANY 2»: если один слейв выключен, мастер блокирует запись >5 сек, ожидая ack недоступного участника. После возврата слейва отложенный коммит завершается.

### Состояние после эксперимента

`ALTER SYSTEM SET synchronous_standby_names=''` + рестарт → replication снова **async** (рабочая конфигурация); слейвы догнали WAL, счётчики сходятся; приложение healthy.

---

# ДЗ: Диалоги и горизонтальное масштабирование (Citus)

## Архитектура

Вместо master/slave репликации (HW-5) — распределённая БД **Citus 14** поверх PostgreSQL 16:

```
                 ┌─────────────────┐
   app :8080 ───►│ citus-coordinator│  (metadata + router, порт 5432)
                 └────────┬────────┘
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
   citus-worker-1  citus-worker-2  citus-worker-3
   (shards)         (shards)         (shards)
```

- Каждый воркер при initdb выполняет `citus_set_coordinator_host('citus-coordinator', 5432)`
- Приложение ходит только в coordinator
- Все ноды запускаются с `wal_level=logical`
- Воркер `citus-worker-4` объявлен под профилем `extra` и поднимается только для решардинга: `docker compose --profile extra up -d citus-worker-4`.

## Схема и шардирование

| Таблица | Тип | Ключ | Примечание |
|---|---|---|---|
| `users` | distributed | `id` | 32 шарда |
| `friends` | distributed | `user_id` | colocated с `users` |
| `posts` | distributed | `user_id` | colocated с `users`; PK `(user_id, id)` — требование Citus |
| `sessions` | reference | — | копии на воркерах |
| `dialog_messages` | distributed | `pair_key` | app-filled `'low:high'`, `colocate_with := 'none'` |

- **Ключ диалога — пара, а не автор.** Приложение нормализует пару в `pair_key = 'low:high'` (Citus 14.2 принимает одну колонку и запрещает GENERATED ALWAYS AS): переписки «звезды» (Леди Гага) разъезжаются по всем 32 шардам вместо hot-shard на одного автора, а `send`/`list` всегда содержат `pair_key` в WHERE → single-shard.

## Решардинг без даунтайма

`rebalance_table_shards('<таблица>')` переносит шарды colocation-группы через **logical replication**: чтение и запись продолжают работать, а на промоушене подписчика Citus берёт **короткий write-lock на перемещаемый шард**. Запросы, попавшие в это окно, Citus **не ретраит** — ретраить должен клиент (в точечном режиме `block_writes` шард-источник блокируется на всю копию). Перенос одного шарда вручную: `citus_move_shard_placement(<shardid>, 'from', 5432, 'to', 5432, 'auto')`.

Предпосылки, без которых онлайн-перенос не работает 

1. **`wal_level=logical` на всех нодах.** При дефолтном `replica` режим `auto` не откатывается на `block_writes`, а падает: `ERROR: logical decoding requires wal_level >= logical`.
2. **Межузловая аутентификация.** Перенос выполняется на воркере-источнике, который подключается к воркеру-цели, а Citus **не переиспользует пароль клиента** — без межузловых кредей `fe_sendauth: no password supplied`. Решение: `postgres/.pgpass` монтируется на каждую ноду и при старте ставится в `$HOME/.pgpass` (0600 — git не хранит режим файла, а libpq игнорирует `.pgpass` с групповым доступом) + `PGPASSWORD` в окружении как страховка.
3. **PRIMARY KEY или REPLICA IDENTITY у каждой таблицы colocation-группы.** Иначе перенос падает: `cannot use logical replication to transfer shards of the relation friends …`, а таблица без ключа делает неребалансируемой всю группу `users` (users+friends+posts). У `friends` — PK `(user_id, friend_id)` с distribution-колонкой в ключе, как того требует Citus.

Скрипты в `scripts/citus/`:

- `rebalance.sql` — показывает размещение и объём шардов (`citus_shards`), ребалансирует группы (группа `users` — одним вызовом, `dialog_messages` — отдельно) и проверяет результат; в комментариях — точечный перенос, `alter_distributed_table`, вывод ноды.
- `add_worker.sh [host] [port]` — поднимает воркер (профиль `extra`), ждёт готовности (таймаут → явная ошибка), проверяет `wal_level` на координаторе **и** новом воркере, делает `citus_set_coordinator_host` → идемпотентный `citus_add_node` → ребалансировку.

Сопутствующее: сменить **число** шардов — `alter_distributed_table('users', shard_count => 64, cascade_to_colocated => true)` (in-place; `ALTER TABLE … SET (citus.shard_count = N)` не работает — это GUC, а не reloption); плановый вывод ноды — `citus_drain_node` + `citus_remove_node`, физически потерянную — `citus_disable_node` → ребалансировка → `citus_remove_node`. `shard_count` выбирается с запасом (~20–30 GB на шард), рост покрывается добавлением нод. `dialog_messages` не colocated и не участвует в scatter-запросах — её `shard_count` можно поднять независимо (64/128), уменьшив хеш-скью.

## Результаты

### Scale-out / scale-in под нагрузкой

```bash
docker compose --profile extra up -d citus-worker-4   # 4-я нода
scripts/citus/add_worker.sh citus-worker-4 5432       # регистрация + ребалансировка
```

| Шаг | Результат |
|---|---|
| `add_worker.sh` (регистрация + ребалансировка 2 групп) | **63 с**; шарды разъехались ровно по 8 на каждую таблицу на каждую из 4 нод; приложение **не перезапускалось** |
| Вывод ноды: `citus_drain_node` + `citus_remove_node` + ребалансировка на 3 ноды | **55 с**; вернулись 11/11/10, `pg_dist_node` снова 3 записи |
| Целостность данных | `users` 999 069, `friends` 7 880, `posts` 167 841, `dialog_messages` 46 685 — без потерь |
| Colocation | проверено по `shardminvalue`: 0 рассинхронов `users`/`friends`/`posts` после scale-out и scale-in |
| Доступность приложения | параллельно работала «молотилка»: 6 000 итераций (12 000 запросов `send` + `list`) → **0 ответов, отличных от 200** |

Перенос одного шарда: `dialog_messages` (13 МБ) в режиме `auto` — **3.6 с**; шард colocated-группы `users` (users+friends+posts, 425 МБ) — **6.2 с**; `rebalance_table_shards('dialog_messages')` с реальным дисбалансом (11/11/10 → 10/12/10) — **4.3 с**.

Ноль ошибок доказывает отсутствие *сбоев*, а не *задержек*: в окно промоушена запросы ждут write-lock; отдельно latency переезда не измерялась.

### «Эффект Леди Гаги»: распределение и честный скью

Тест (`load-test/jmeter/dialog_load.jmx`): звезда + 50 peers, 100 потоков, 60 с — peers пишут звезде и читают диалоги, звезда отвечает случайным peers. Итог: **49 413 запросов, 0 ошибок** — 100 потоков на 25-соединительном пуле Hikari отрабатывают очередью, а не отказами.

| Группа запросов | Запросов | Ошибок | avg, мс | p95, мс | p99, мс | max, мс | req/s |
|---|---|---|---|---|---|---|---|
| peer lists dialog with gaga | 25 214 | 0 | 218 | 686 | 1083 | 2564 | 355 |
| peer sends to gaga | 23 569 | 0 | 234 | 730 | 1112 | 3184 | 332 |
| gaga sends to peer | 280 | 0 | 214 | 668 | 1229 | 2016 | 3.9 |
| gaga lists dialog | 248 | 0 | 242 | 713 | 992 | 1477 | 3.5 |

Состояние кластера на момент замера — 103 пары, 46 685 сообщений, из них 6 000 в одном «горячем» диалоге:

```
 citus-worker-1 |     11 | 8608 kB     -- 24.9 % данных
 citus-worker-2 |     11 | 13 MB       -- 39.9 %
 citus-worker-3 |     10 | 12 MB       -- 35.2 %
```

По шардам: min 32 kB, avg 1080 kB, max 3712 kB (`max/avg = 3.44`).

1. **Эффект звезды снят на уровне ключа**: переписки Гаги есть на всех нодах, а не собраны в один hot-shard — это и была цель выбора `pair_key`.
2. **Ребалансировка выравнивает количество шардов, а не объём данных.** Разброс по нодам 24.9/35.2/39.9 %.
3. **Один горячий диалог ключом не лечится**: крупнейший шард (3.7 МБ при среднем 1.08 МБ) — это «горячий» диалог с 6 000 сообщений, вся его нагрузка легла на один шард

## Проверка на живом кластере

```bash
# single-shard роутинг — pruning работает и для bind-параметра (Task Count: 1)
docker compose exec -T citus-coordinator psql -U otus -d otus_highload -c \
 "PREPARE q(text) AS SELECT id FROM dialog_messages WHERE pair_key=\$1
  ORDER BY created_at ASC, id ASC OFFSET 0 LIMIT 10; EXPLAIN (COSTS OFF) EXECUTE q('x:y');"

# число шардов и объём данных по нодам (число шардов и байты — разные вещи)
docker compose exec -T citus-coordinator psql -U otus -d otus_highload -c \
 "SELECT nodename, count(*) shards, pg_size_pretty(sum(shard_size)) data
  FROM citus_shards WHERE table_name='dialog_messages'::regclass GROUP BY 1 ORDER BY 1;"

# координатор не хранит данных (занимает килобайты)
docker compose exec -T citus-coordinator psql -U otus -d otus_highload -c \
 "SELECT relname, pg_size_pretty(pg_total_relation_size(oid)) FROM pg_class
  WHERE relname IN ('users','posts','friends','dialog_messages') ORDER BY 2 DESC;"

# решардинг: новый воркер (профиль extra) → регистрация → ребалансировка
scripts/citus/add_worker.sh citus-worker-4 5432

```
