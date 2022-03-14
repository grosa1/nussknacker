package pl.touk.nussknacker.engine.management.periodic

import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration

case class PeriodicBatchConfig(db: Config,
                               rescheduleCheckInterval: FiniteDuration,
                               deployInterval: FiniteDuration,
                               executionConfig: PeriodicExecutionConfig,
                               jarsDir: String)

case class PeriodicExecutionConfig(rescheduleOnFailure: Boolean = false)
