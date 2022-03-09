package pl.touk.nussknacker.ui.api.helpers

import io.circe.{Encoder, Json}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.{Deploy, ProcessActionType}
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData, RequestResponseMetaData, ScenarioSpecificData, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node.{NodeData, SubprocessInputDefinition}
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.processdetails._
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes.{Fraud, RequestResponse, Streaming}
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter

import java.time.LocalDateTime
import scala.util.Random

object TestProcessUtil {

  type ProcessWithJson = BaseProcessDetails[DisplayableProcess]

  private val RandomGenerator = new Random()

  def toJson(espProcess: EspProcess, processingType: ProcessingType = TestProcessingTypes.Streaming): Json =
    Encoder[DisplayableProcess].apply(toBaseDisplayable(espProcess, processingType))

  def toBaseDisplayable(espProcess: EspProcess, processingType: ProcessingType = TestProcessingTypes.Streaming): DisplayableProcess =
    ProcessConverter.toDisplayable(espProcess.toCanonicalProcess, processingType)

  def displayableToBaseProcess(displayable: DisplayableProcess, category: Category = TestCategories.Category1, isArchived: Boolean = false): ProcessDetails =
    baseDisplayable(displayable.id, category, isArchived = isArchived, processingType = displayable.processingType, json = Some(displayable))

  def validatedToBaseProcess(displayable: ValidatedDisplayableProcess) : ValidatedProcessDetails =
    baseDisplayable(displayable.id, processingType = displayable.processingType).copy(json = displayable)

  def baseDisplayable(name: String, category: Category = TestCategories.Category1, isArchived: Boolean = false,
                      processingType: ProcessingType = Streaming, json: Option[DisplayableProcess] = None, lastAction: Option[ProcessActionType] = None,
                      description: Option[String] = None, history: Option[List[ProcessVersion]] = None) : ProcessDetails = {
    val jsonData = json.map(_.copy(id = name, processingType = processingType)).getOrElse(createEmptyDisplayable(name, processingType))
    createBaseProcess(name, category, isSubprocess = false, isArchived, processingType, jsonData, lastAction, description, history)
  }

  def baseDisplayableSubprocess(name: String, category: Category, isArchived: Boolean = false, processingType: String = Streaming, json: Option[DisplayableProcess] = None, lastAction: Option[ProcessActionType] = None): BaseProcessDetails[DisplayableProcess] = {
    val jsonData = json.map(_.copy(id = name, processingType = processingType)).getOrElse(createEmptyDisplayableSubprocess(name, processingType))
    createBaseProcess(name, category, isSubprocess = true, isArchived, processingType, jsonData, lastAction)
  }
  
  def baseCanonical(name: String, category: Category = TestCategories.Category1, isSubprocess: Boolean = false, isArchived: Boolean = false,
                    processingType: ProcessingType = Streaming, json: Option[CanonicalProcess] = None, lastAction: Option[ProcessActionType] = None,
                    description: Option[String] = None, history: Option[List[ProcessVersion]] = None) : BaseProcessDetails[CanonicalProcess] = {
    val jsonData = json.map(j => j.copy(metaData = j.metaData.copy(id = name))).getOrElse(createEmptyCanonical(name, processingType))
    createBaseProcess(name, category, isSubprocess, isArchived, processingType, jsonData, lastAction, description, history)
  }

  def createBaseProcess[ProcessShape](name: String, category: Category = TestCategories.Category1, isSubprocess: Boolean = false, isArchived: Boolean = false,
                                      processingType: ProcessingType = Streaming, json: ProcessShape = Unit, lastAction: Option[ProcessActionType] = None,
                                      description: Option[String] = None, history: Option[List[ProcessVersion]] = None): BaseProcessDetails[ProcessShape] =
    BaseProcessDetails[ProcessShape](
      id = name,
      name = name,
      processId = ProcessId(generateId()),
      processVersionId = VersionId.initialVersionId,
      isLatestVersion = true,
      description = description,
      isArchived = isArchived,
      isSubprocess = isSubprocess,
      processingType = processingType,
      processCategory = category,
      modificationDate = LocalDateTime.now(),
      modifiedAt = LocalDateTime.now(),
      modifiedBy = "user1",
      createdAt = LocalDateTime.now(),
      createdBy = "user1",
      tags = List(),
      lastAction = lastAction.map(createProcessAction),
      lastDeployedAction = lastAction.collect {
        case Deploy => createProcessAction(Deploy)
      },
      json = json,
      history = history.getOrElse(Nil),
      modelVersion = None
    )

  def createEmptyCanonical(id: String, processingType: ProcessingType = Streaming): CanonicalProcess = {
    val scenarioSpecificData = createSpecificData(processingType)
    CanonicalProcess(MetaData(id, scenarioSpecificData), Nil)
  }

  def createEmptyDisplayable(id: String, processingType: ProcessingType = Streaming): DisplayableProcess = {
    val scenarioSpecificData = createSpecificData(processingType)
    DisplayableProcess(id, ProcessProperties(scenarioSpecificData), Nil, Nil, processingType)
  }

  private def createSpecificData(processingType: ProcessingType): ScenarioSpecificData = processingType match {
    case RequestResponse => RequestResponseMetaData(None)
    case Streaming | Fraud => StreamMetaData()
    case _ => throw new IllegalArgumentException(s"Unknown processing type: $processingType.")
  }

  def createEmptyDisplayableSubprocess(name: String, processingType: ProcessingType): DisplayableProcess =
    createDisplayableSubprocess(name, List(SubprocessInputDefinition("input", List(SubprocessParameter("in", SubprocessClazzRef[String])))), processingType)

  def createDisplayableSubprocess(name: String, nodes: List[NodeData], processingType: ProcessingType): DisplayableProcess =
    DisplayableProcess(name, ProcessProperties(FragmentSpecificData()), nodes, Nil, processingType)

  def createProcessAction(action: ProcessActionType): ProcessAction = ProcessAction(
    processVersionId = VersionId(generateId()),
    performedAt = LocalDateTime.now(),
    user = "user",
    action = action,
    commentId = None,
    comment = None,
    buildInfo = Map.empty
  )

  private def generateId() = Math.abs(RandomGenerator.nextLong())
}
