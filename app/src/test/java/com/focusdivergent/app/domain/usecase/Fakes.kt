package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.alarm.ObligationAlarmScheduler
import com.focusdivergent.app.domain.device.DeviceState
import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.InstalledApp
import com.focusdivergent.app.domain.model.PendingLimitChange
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.model.TimeExtension
import com.focusdivergent.app.domain.model.UsageSnapshot
import com.focusdivergent.app.domain.realitycheck.RealityCheckSettings
import com.focusdivergent.app.domain.repository.AppLimitRepository
import com.focusdivergent.app.domain.repository.InstalledAppsRepository
import com.focusdivergent.app.domain.repository.PendingLimitChangeRepository
import com.focusdivergent.app.domain.repository.RoutineCompletionRepository
import com.focusdivergent.app.domain.repository.TaskRepository
import com.focusdivergent.app.domain.repository.TimeExtensionRepository
import com.focusdivergent.app.domain.repository.UsageRepository
import com.focusdivergent.app.domain.settings.DailyEnergyStore
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.yield

/** Dobles de test compartidos por los tests de casos de uso. */

/** «Ahora» fijo: mediodía UTC del 10 de octubre de 2026. Hoy empieza a las 00:00 UTC de ese día. */
val TEST_NOW: Instant = Instant.parse("2026-10-10T12:00:00Z")
val TEST_CLOCK: Clock = Clock.fixed(TEST_NOW, ZoneOffset.UTC)
val TEST_START_OF_DAY: Instant = Instant.parse("2026-10-10T00:00:00Z")

class FakeAppLimits(limits: List<AppLimit> = emptyList()) : AppLimitRepository {
    private val state = MutableStateFlow(limits.associateBy { it.packageName })
    val items: Map<String, AppLimit> get() = state.value

    override fun observeAll(): Flow<List<AppLimit>> = state.map { it.values.toList() }
    override suspend fun getByPackage(packageName: String): AppLimit? = items[packageName]
    override suspend fun save(limit: AppLimit) {
        state.value = items + (limit.packageName to limit)
    }
    override suspend fun delete(limit: AppLimit) {
        state.value = items - limit.packageName
    }
}

/** Cambios de límite pendientes, en memoria. */
class FakePendingLimitChanges(initial: List<PendingLimitChange> = emptyList()) : PendingLimitChangeRepository {
    private val state = MutableStateFlow(initial.associateBy { it.packageName })
    val items: Map<String, PendingLimitChange> get() = state.value

    override fun observeAll(): Flow<List<PendingLimitChange>> = state.map { it.values.toList() }
    override suspend fun get(packageName: String): PendingLimitChange? = items[packageName]
    override suspend fun getDue(now: Instant): List<PendingLimitChange> = items.values.filter { it.appliesAt <= now }
    override suspend fun save(change: PendingLimitChange) {
        state.value = items + (change.packageName to change)
    }
    override suspend fun delete(packageName: String) {
        state.value = items - packageName
    }
}

/** Apps instaladas de mentira. [labels] da el nombre de cada paquete. */
class FakeInstalledApps(private val apps: List<InstalledApp> = emptyList()) : InstalledAppsRepository {
    override suspend fun launchableApps(): List<InstalledApp> = apps
    override suspend fun labelOf(packageName: String): String? = apps.firstOrNull { it.packageName == packageName }?.label
}

/**
 * @param yieldOnRead cede el turno en cada lectura, como haría una base de datos real: así las corrutinas
 * concurrentes se entrelazan entre «leer» y «escribir» y se puede probar la atomicidad.
 */
class FakeTimeExtensions(
    initial: List<TimeExtension> = emptyList(),
    private val yieldOnRead: Boolean = false,
) : TimeExtensionRepository {
    val items = initial.toMutableList()
    var reads = 0
        private set

    override suspend fun getSince(packageName: String, since: Instant): List<TimeExtension> {
        reads++
        val snapshot = items.filter { it.packageName == packageName && it.grantedAt >= since }
        if (yieldOnRead) yield()
        return snapshot
    }

    override suspend fun add(extension: TimeExtension): Long {
        val id = items.size + 1L
        items += extension.copy(id = id)
        return id
    }
}

/** Una ampliación de [minutes] minutos concedida [hoursAgo] horas antes de [TEST_NOW]. */
fun extension(packageName: String, hoursAgo: Long = 1, minutes: Int = 15) =
    TimeExtension(packageName = packageName, grantedAt = TEST_NOW.minusSeconds(hoursAgo * 3600), minutes = minutes)

class FakeUsage(private val snapshot: UsageSnapshot) : UsageRepository {
    var reads = 0
        private set

    override suspend fun readToday(): UsageSnapshot {
        reads++
        return snapshot
    }
}

class FakeDeviceState(
    var interactive: Boolean = true,
    var inCall: Boolean = false,
    var safe: Set<String> = emptySet(),
) : DeviceState {
    override fun isInteractive() = interactive
    override fun isInCall() = inCall
    override fun safePackages() = safe
}

/** Repositorio de tareas en memoria, con las mismas reglas que el de Room para las consultas de Obligaciones. */
class FakeTasks(initial: List<Task> = emptyList()) : TaskRepository {
    private val state = MutableStateFlow(initial)
    val items: List<Task> get() = state.value

    override fun observeAll(): Flow<List<Task>> = state
    override fun observeByEnergy(energyLevel: EnergyLevel): Flow<List<Task>> =
        state.map { list -> list.filter { it.energyLevel == energyLevel } }
    override fun observeByType(type: TaskType): Flow<List<Task>> = state.map { list -> list.filter { it.type == type } }
    override fun observeByTypeBetween(type: TaskType, from: Instant, to: Instant): Flow<List<Task>> =
        state.map { list -> list.filter { it.type == type && it.startTime != null && it.startTime >= from && it.startTime <= to } }

    override suspend fun getById(id: Long): Task? = items.firstOrNull { it.id == id }

    override suspend fun getPendingObligationsStartingBetween(from: Instant, to: Instant): List<Task> =
        items.filter { it.isPendingObligation() && it.startTime!! > from && it.startTime <= to }
            .sortedBy { it.startTime }

    override suspend fun getNextPendingObligationStartingAfter(after: Instant): Task? =
        items.filter { it.isPendingObligation() && it.startTime!! > after }.minByOrNull { it.startTime!! }

    private fun Task.isPendingObligation() =
        type == TaskType.OBLIGATION && status == TaskStatus.PENDING && startTime != null

    override suspend fun save(task: Task): Long {
        val id = if (task.id != 0L) task.id else (items.maxOfOrNull { it.id } ?: 0L) + 1
        state.value = items.filterNot { it.id == id } + task.copy(id = id)
        return id
    }

    override suspend fun updateStatus(id: Long, status: TaskStatus, completedAt: Instant?) {
        state.value = items.map { if (it.id == id) it.copy(status = status, completedAt = completedAt) else it }
    }

    override suspend fun delete(task: Task) {
        state.value = items.filterNot { it.id == task.id }
    }
}

/** Rutinas hechas por día, en memoria. */
class FakeRoutineCompletions : RoutineCompletionRepository {
    private val state = MutableStateFlow<Set<Pair<Long, LocalDate>>>(emptySet())
    val items: Set<Pair<Long, LocalDate>> get() = state.value

    override fun observeCompletedOn(day: LocalDate): Flow<Set<Long>> =
        state.map { set -> set.filter { it.second == day }.map { it.first }.toSet() }

    override fun observeCompletedDays(): Flow<List<LocalDate>> =
        state.map { set -> set.map { it.second }.distinct().sorted() }

    override suspend fun getCompletedDays(): List<LocalDate> = items.map { it.second }.distinct().sorted()

    override suspend fun markCompleted(taskId: Long, day: LocalDate, at: Instant) {
        state.value = items + (taskId to day)
    }

    override suspend fun unmarkCompleted(taskId: Long, day: LocalDate) {
        state.value = items - (taskId to day)
    }
}

/** Una actividad de prueba. [startsInMinutes] se cuenta desde [TEST_NOW]; nulo = sin fecha. */
fun task(
    id: Long,
    title: String = "Actividad $id",
    type: TaskType = TaskType.TASK,
    energy: EnergyLevel = EnergyLevel.MEDIUM,
    status: TaskStatus = TaskStatus.PENDING,
    startsInMinutes: Long? = null,
    recurrenceRule: String? = null,
    estimatedMinutes: Int? = null,
    completedAt: Instant? = null,
) = Task(
    id = id,
    title = title,
    type = type,
    energyLevel = energy,
    status = status,
    startTime = startsInMinutes?.let { TEST_NOW.plusSeconds(it * 60) },
    recurrenceRule = recurrenceRule,
    estimatedMinutes = estimatedMinutes,
    completedAt = completedAt,
)

/** [TEST_NOW] cae en sábado 10 de octubre de 2026. */
val TEST_TODAY: LocalDate = LocalDate.of(2026, 10, 10)

/** Una Obligación que empieza [startsInMinutes] minutos después de [TEST_NOW] (negativo = ya empezó). */
fun obligation(
    id: Long,
    startsInMinutes: Long,
    title: String = "Cita $id",
    status: TaskStatus = TaskStatus.PENDING,
    type: TaskType = TaskType.OBLIGATION,
) = Task(
    id = id,
    title = title,
    type = type,
    energyLevel = EnergyLevel.MEDIUM,
    status = status,
    startTime = TEST_NOW.plusSeconds(startsInMinutes * 60),
)

class FakeAlarmScheduler : ObligationAlarmScheduler {
    var scheduledAt: Instant? = null
        private set
    var cancels = 0
        private set

    override fun schedule(at: Instant) {
        scheduledAt = at
    }

    override fun cancel() {
        scheduledAt = null
        cancels++
    }
}

class FakeRealityCheckSettings(
    override var thresholdMinutes: Int = 20,
    override var silenceMinutes: Int = 20,
) : RealityCheckSettings

/** La energía del día en memoria: guarda solo la última elegida y el día en que se eligió. */
class FakeDailyEnergyStore(initial: Pair<LocalDate, EnergyLevel>? = null) : DailyEnergyStore {
    private val state = MutableStateFlow(initial)

    override fun observe(day: LocalDate): Flow<EnergyLevel?> = state.map { it?.takeIf { stored -> stored.first == day }?.second }
    override fun get(day: LocalDate): EnergyLevel? = state.value?.takeIf { it.first == day }?.second
    override fun set(day: LocalDate, level: EnergyLevel) {
        state.value = day to level
    }
}
