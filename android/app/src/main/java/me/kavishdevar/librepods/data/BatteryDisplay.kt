package me.kavishdevar.librepods.data

/** Zero is a valid measurement. Unknown/invalid levels and disconnected components are separate. */
fun Battery.hasReportedLevel(): Boolean = status != BatteryStatus.DISCONNECTED && level in 0..100

fun List<Battery>.combinedBudsBattery(): Battery? {
    val buds = filter {
        (it.component == BatteryComponent.LEFT || it.component == BatteryComponent.RIGHT) && it.hasReportedLevel()
    }
    val lowest = buds.minByOrNull { it.level } ?: return null
    return Battery(
        BatteryComponent.LEFT,
        lowest.level,
        if (buds.all { it.status == BatteryStatus.CHARGING }) BatteryStatus.CHARGING else BatteryStatus.NOT_CHARGING
    )
}
