package com.suncanvas.app.data

import com.suncanvas.app.data.db.WidgetDao
import com.suncanvas.app.data.db.WidgetDesignEntity
import com.suncanvas.app.data.db.WidgetInstanceEntity
import com.suncanvas.app.widget.model.WidgetSpec
import com.suncanvas.app.widget.model.WidgetTemplate
import com.suncanvas.app.widget.model.WidgetTemplates
import kotlinx.coroutines.flow.Flow

class WidgetRepository(
    private val dao: WidgetDao,
    private val settings: SettingsRepository,
) {
    val designs: Flow<List<WidgetDesignEntity>> = dao.observeDesigns()
    val instances: Flow<List<WidgetInstanceEntity>> = dao.observeInstances()

    suspend fun design(id: Long) = dao.getDesign(id)
    suspend fun getDesigns() = dao.getDesigns()

    suspend fun saveDesign(spec: WidgetSpec, id: Long? = null): Long {
        val existing = id?.let { dao.getDesign(it) }
        return if (existing != null) {
            dao.updateDesign(existing.copy(name = spec.name, specJson = spec.toJson(), updatedAt = System.currentTimeMillis()))
            existing.id
        } else {
            dao.insertDesign(WidgetDesignEntity(name = spec.name, specJson = spec.toJson()))
        }
    }

    suspend fun duplicateDesign(id: Long): Long? {
        val d = dao.getDesign(id) ?: return null
        val spec = WidgetSpec.fromJson(d.specJson) ?: return null
        val copy = spec.copy(name = "${spec.name} copy")
        return dao.insertDesign(WidgetDesignEntity(name = copy.name, specJson = copy.toJson()))
    }

    suspend fun deleteDesign(id: Long) = dao.deleteDesign(id)

    fun observeInstance(appWidgetId: Int) = dao.observeInstance(appWidgetId)
    suspend fun instance(appWidgetId: Int) = dao.getInstance(appWidgetId)
    suspend fun getInstances() = dao.getInstances()

    /**
     * Returns the stored configuration for a placed widget, creating one on first use. A design the
     * user just asked to pin from Widget Studio takes priority over the receiver's default template.
     */
    suspend fun getOrCreateInstance(appWidgetId: Int, fallback: WidgetTemplate): WidgetInstanceEntity {
        dao.getInstance(appWidgetId)?.let { return it }
        val pending = settings.takePendingPin()
        val spec = pending?.first?.let { WidgetSpec.fromJson(it) } ?: fallback.spec
        val entity = WidgetInstanceEntity(appWidgetId = appWidgetId, specJson = spec.toJson(), sourceDesignId = pending?.second)
        dao.putInstance(entity)
        return entity
    }

    /** Saves a spec for one widget only. Other widgets keep their own copies. */
    suspend fun saveInstance(appWidgetId: Int, spec: WidgetSpec, sourceDesignId: Long? = null) {
        val existing = dao.getInstance(appWidgetId)
        dao.putInstance(
            WidgetInstanceEntity(
                appWidgetId = appWidgetId,
                specJson = spec.toJson(),
                sourceDesignId = sourceDesignId ?: existing?.sourceDesignId,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun deleteInstance(appWidgetId: Int) = dao.deleteInstance(appWidgetId)

    /** Removes rows for widgets that no longer exist on any home screen. */
    suspend fun pruneInstances(validIds: Set<Int>) {
        dao.getInstances().filter { it.appWidgetId !in validIds }.forEach { dao.deleteInstance(it.appWidgetId) }
    }

    fun templateFor(id: String?) = id?.let { WidgetTemplates.byId(it) } ?: WidgetTemplates.default
}
