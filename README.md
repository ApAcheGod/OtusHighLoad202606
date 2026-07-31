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
| GET | `/user/get/{id}` | да (Bearer token) | Получение анкеты |
| GET | `/user/search?first_name=...&last_name=...` | да (Bearer token) | Поиск анкет |
