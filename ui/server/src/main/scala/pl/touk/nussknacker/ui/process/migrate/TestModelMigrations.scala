package pl.touk.nussknacker.ui.process.migrate

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.nussknacker.ui.process.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessDetails
import pl.touk.nussknacker.ui.process.subprocess.{SubprocessRepository, SubprocessResolver}
import pl.touk.nussknacker.ui.validation.ProcessValidation
import pl.touk.nussknacker.ui.validation.ValidationResults.{NodeValidationError, ValidationErrors, ValidationResult, ValidationWarnings}

object TestModelMigrations {

  def apply(modelData: Map[ProcessingType, ModelData]) : TestModelMigrations = {
    new TestModelMigrations(modelData.mapValues(_.migrations).collect {
      case (k, Some(migration)) => (k, migration)
    }, modelData.mapValues(_.validator))
  }

}

class TestModelMigrations(migrations: Map[ProcessingType, ProcessMigrations], validators: Map[ProcessingType, ProcessValidator]) {


  def testMigrations(processes: List[ProcessDetails], subprocesses: List[ProcessDetails]) : List[TestMigrationResult] = {

    val validation = new ProcessValidation(validators, new SubprocessResolver(prepareSubprocessRepository(subprocesses)))

    processes.flatMap(testSingleMigration(validation) _)
  }

  private def testSingleMigration(validation: ProcessValidation)(process: ProcessDetails) : Option[TestMigrationResult] = {
    val migrator = new SingleProcessMigrator(migrations)

    for {
      previousResult <- process.json.flatMap(_.validationResult)
      SingleMigrationResult(newProcess, migrations) <- migrator.migrateProcess(process)
      displayable = ProcessConverter.toDisplayable(newProcess, process.processingType)
      validated = displayable.validated(validation)
    } yield TestMigrationResult(validated, extractNewErrors(previousResult, validated.validationResult.get), migrations.exists(_.failOnNewValidationError))


  }

  private def prepareSubprocessRepository(subprocesses: List[ProcessDetails]) = {
    val canonicalSubprocesses = subprocesses.flatMap(_.json).map(ProcessConverter.fromDisplayable).toSet
    new SubprocessRepository {
      override def loadSubprocesses(): Set[CanonicalProcess] = canonicalSubprocesses
    }
  }

  private def extractNewErrors(before: ValidationResult, after: ValidationResult) : ValidationResult = {

    def diffOnMap(before: Map[String, List[NodeValidationError]], after: Map[String, List[NodeValidationError]]) = {
      after.map {
        case (nodeId, errors) => (nodeId, errors.diff(before.getOrElse(nodeId, List())))
      }.filterNot(_._2.isEmpty)
    }

    ValidationResult(
      ValidationErrors(
        diffOnMap(before.errors.invalidNodes, after.errors.invalidNodes),
        after.errors.processPropertiesErrors.diff(before.errors.processPropertiesErrors),
        after.errors.globalErrors.diff(before.errors.globalErrors)
      ),
      ValidationWarnings(diffOnMap(before.warnings.invalidNodes, after.warnings.invalidNodes))
    )
  }


}

case class TestMigrationResult(converted: DisplayableProcess, newErrors: ValidationResult, shouldFail: Boolean)

