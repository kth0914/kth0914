package com.originos.globalizer.core

enum class Status { OK, WARNING, MISSING, UNKNOWN }
enum class Risk { LOW, MEDIUM, HIGH }
enum class RestorePolicy { ALWAYS, ONLY_IF_OLD_EMPTY }

data class ServiceCheck(
    val title: String,
    val packageName: String,
    val status: Status,
    val detail: String
)

data class DeviceReport(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val originOsVersion: String,
    val checks: List<ServiceCheck>
) {
    val score: Int
        get() = if (checks.isEmpty()) 0 else checks.count { it.status == Status.OK } * 100 / checks.size
}

data class Tweak(
    val id: String,
    val title: String,
    val description: String,
    val risk: Risk,
    val readCommand: String,
    val applyCommand: String,
    val restoreTemplate: String,
    val restorePolicy: RestorePolicy = RestorePolicy.ALWAYS,
    val fallbackApplyCommand: String? = null,
    val fallbackRestoreTemplate: String? = null,
    val fallbackLabel: String = "替代模式"
)

data class SnapshotEntry(
    val tweakId: String,
    val oldValue: String,
    val timestamp: Long,
    val appliedMode: String = "primary"
)
