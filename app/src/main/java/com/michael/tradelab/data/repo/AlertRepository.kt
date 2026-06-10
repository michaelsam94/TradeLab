package com.michael.tradelab.data.repo

import com.michael.tradelab.data.local.AlertDao
import com.michael.tradelab.data.local.AlertEntity
import com.michael.tradelab.domain.model.Alert
import com.michael.tradelab.domain.model.AlertKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertRepository @Inject constructor(
    private val dao: AlertDao,
) {
    val alerts: Flow<List<Alert>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun create(alert: Alert): Long = dao.insert(alert.toEntity())
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun activeAlerts(): List<Alert> = dao.active().map { it.toModel() }
    suspend fun markTriggered(id: Long) = dao.markTriggered(id, System.currentTimeMillis())

    companion object {
        fun AlertEntity.toModel() = Alert(id, symbol, AlertKind.valueOf(kind), level, interval, enabled, triggeredAt)
        fun Alert.toEntity() = AlertEntity(id, symbol, kind.name, level, interval, enabled, triggeredAt)
    }
}
