-- 1. Текущее размещение и размер шардов.
--    В Citus 14.2 нет pg_dist_placement_metadata; удобнее всего citus_shards.
SELECT nodename, count(*) AS shards, pg_size_pretty(sum(shard_size)) AS data
FROM citus_shards
GROUP BY 1
ORDER BY 1;

SELECT table_name, citus_table_type, distribution_column, colocation_id, shard_count
FROM citus_tables
ORDER BY citus_table_type, table_name;

-- 2. Ребалансировка.
--    users, friends и posts — одна colocation-группа: шарды переезжают вместе,
--    поэтому достаточно одного вызова на группу (вызовы для friends/posts были бы no-op).
SELECT rebalance_table_shards('users');
--    dialog_messages — отдельная colocation-группа (colocate_with := 'none').
SELECT rebalance_table_shards('dialog_messages');

-- 2b. Современный способ для всего кластера сразу (асинхронно).
--     Статус: SELECT * FROM citus_rebalance_status();  Ожидание: SELECT citus_rebalance_wait();
-- SELECT citus_rebalance_start();
-- SELECT citus_rebalance_wait();

-- 3. Проверка результата: число шардов на ноду и объём данных.
SELECT nodename, count(*) AS shards, pg_size_pretty(sum(shard_size)) AS data
FROM citus_shards
GROUP BY 1
ORDER BY 1;

-- 4. Точечный перенос одного шарда (например, чтобы разгрузить воркер):
-- SELECT citus_move_shard_placement(<shardid>, 'citus-worker-1', 5432, 'citus-worker-3', 5432, 'auto');

-- 5. Смена ЧИСЛА шардов делается не переносом placement, а
--    alter_distributed_table(..., shard_count => N) — in-place, без swap-таблиц:
-- SELECT alter_distributed_table('dialog_messages', shard_count => 64);
--    Для colocation-группы целиком: cascade_to_colocated => true.
--    (ALTER TABLE ... SET (citus.shard_count = N) не поддерживается: citus.shard_count — GUC,
--     PostgreSQL отвечает "unrecognized parameter namespace citus".)

-- 6. Если нода потеряна физически:
-- SELECT citus_disable_node('citus-worker-N', 5432);   -- помечает ноду неактивной
-- SELECT rebalance_table_shards('users');               -- шарды уезжают на живые ноды
-- SELECT citus_remove_node('citus-worker-N', 5432);     -- окончательный вывод ноды
-- Для планового вывода ноды используйте citus_drain_node (citus_disable_node — не drain).
