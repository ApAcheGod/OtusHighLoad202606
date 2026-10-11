package ru.otus.highload.app.config

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource
import org.springframework.transaction.support.TransactionSynchronizationManager
import javax.sql.DataSource
import java.util.concurrent.atomic.AtomicInteger

class ReplicationRoutingDataSource(
    master: DataSource,
    slaves: List<DataSource>,
) : AbstractRoutingDataSource() {

    private val readSequence = AtomicInteger()
    private val slaveCount = slaves.size

    init {
        setDefaultTargetDataSource(master)
        val targets = HashMap<Any, Any>()
        targets[MASTER] = master
        slaves.forEachIndexed { index, slave ->
            targets[slaveKey(index)] = slave
        }
        setTargetDataSources(targets)
    }

    override fun determineCurrentLookupKey(): Any? {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            val pair = readSequence.getAndIncrement() / 2
            return slaveKey(Math.floorMod(pair, slaveCount))
        }
        return MASTER
    }

    private fun slaveKey(index: Int) = SLAVE_PREFIX + index

    private companion object {
        const val MASTER = "master"
        const val SLAVE_PREFIX = "slave_"
    }
}