package io.github.mohamtur1.fourcbon2.local

/** Serialization for [JobRecord] in the local document store format. */
object JobRecordCodec {
    private val FIELD_COUNT = 12

    fun encode(job: JobRecord): String = RecordCodec.join(
        listOf(
            job.id,
            job.ownerAccount,
            job.goal,
            job.workflowVersion.toString(),
            RecordCodec.joinMap(job.inputs),
            RecordCodec.joinMap(job.artifactHashes),
            job.baseRevision,
            "${job.resourceCaps.maxActions};${job.resourceCaps.maxUploadBytes};${job.resourceCaps.maxMinutes}",
            RecordCodec.joinList(job.dependencies),
            job.deadlineEpochMs.toString(),
            job.authorizationScope,
            job.state.name,
        ),
    )

    fun decode(line: String): JobRecord {
        val fields = RecordCodec.split(line)
        require(fields.size == FIELD_COUNT) { "Invalid job record." }
        val capsParts = fields[7].split(";")
        require(capsParts.size == 3) { "Invalid resource caps." }
        return JobRecord(
            id = fields[0],
            ownerAccount = fields[1],
            goal = fields[2],
            workflowVersion = fields[3].toInt(),
            inputs = RecordCodec.splitMap(fields[4]),
            artifactHashes = RecordCodec.splitMap(fields[5]),
            baseRevision = fields[6],
            resourceCaps = ResourceCaps(
                maxActions = capsParts[0].toInt(),
                maxUploadBytes = capsParts[1].toLong(),
                maxMinutes = capsParts[2].toLong(),
            ),
            dependencies = RecordCodec.splitList(fields[8]),
            deadlineEpochMs = fields[9].toLong(),
            authorizationScope = fields[10],
            state = JobState.valueOf(fields[11]),
        )
    }
}
