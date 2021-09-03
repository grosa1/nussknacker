package pl.touk.nussknacker.sql

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.sql.db.schema.JdbcMetaDataProviderFactory

class DatabaseEnricherComponentProvider extends ComponentProvider {

  override val providerName: String = "databaseEnricher"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] =
    EnricherComponentFactory.create(config, new JdbcMetaDataProviderFactory())

  override def isCompatible(version: NussknackerVersion): Boolean = true
}
