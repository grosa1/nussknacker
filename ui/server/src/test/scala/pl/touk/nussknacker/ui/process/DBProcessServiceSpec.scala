package pl.touk.nussknacker.ui.process

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.Deploy
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.{MockFetchingProcessRepository, TestFactory}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.subprocess.SubprocessDetails
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

import java.time.Duration
import scala.concurrent.ExecutionContext.Implicits.global

class DBProcessServiceSpec extends FlatSpec with Matchers with PatientScalaFutures {

  import org.scalatest.prop.TableDrivenPropertyChecks._
  import pl.touk.nussknacker.ui.api.helpers.TestCategories._
  import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil._

  //These users were created based on categoriesConfig at ui.conf
  private val adminUser = TestFactory.adminUser()
  private val categoriesUser = TestFactory.userWithCategoriesReadPermission(username = "categoriesUser", categories = catCategories)
  private val testUser = TestFactory.userWithCategoriesReadPermission(username = "categoriesUser", categories = testCategories)
  private val testReqRespUser = TestFactory.userWithCategoriesReadPermission(username = "testReqRespUser", categories = testCategories ++ reqResCategories)

  private val category1Process = baseDisplayable("category1Process", category = Category1, lastAction = Some(Deploy))
  private val category2ArchivedProcess = baseDisplayable("category2ArchivedProcess", isArchived = true, category = Category2)
  private val testSubProcess = baseDisplayableSubprocess("testSubProcess", category = TESTCAT)
  private val reqRespArchivedSubProcess = baseDisplayable("reqRespArchivedSubProcess", isArchived = true, category = ReqRes)

  private val processes: List[ProcessWithJson] = List(
    category1Process, category2ArchivedProcess, testSubProcess, reqRespArchivedSubProcess
  )

  private val subprocessCategory1 = baseDisplayableSubprocess("subprocessCategory1", category = Category1)
  private val subprocessCategory2 = baseDisplayableSubprocess("subprocessCategory2", category = Category2)
  private val subprocessTest = baseDisplayableSubprocess("subprocessTest", category = TESTCAT)
  private val subprocessReqResp = baseDisplayableSubprocess("subprocessReqResp", category = ReqRes)

  private val subprocesses = Set(
    subprocessCategory1, subprocessCategory2, subprocessTest, subprocessReqResp
  )

  private val processCategoryService = new ConfigProcessCategoryService(ConfigWithScalaVersion.config)

  it should "return user processes" in {
    val dBProcessService = createDbProcessService(processes)

    val testingData = Table(
      ("user", "expected"),
      (adminUser, processes),
      (categoriesUser, List(category1Process, category2ArchivedProcess)),
      (testUser, List(testSubProcess)),
      (testReqRespUser, List(testSubProcess, reqRespArchivedSubProcess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessWithJson]) =>
      val result = dBProcessService.getProcesses[DisplayableProcess](user).futureValue
      result shouldBe expected
    }
  }

  it should "return user subprocesses" in {
    val dBProcessService = createDbProcessService[DisplayableProcess](subprocesses.toList)

    val testingData = Table(
      ("user", "subprocesses"),
      (adminUser, subprocesses),
      (categoriesUser, Set(subprocessCategory1, subprocessCategory2)),
      (testUser, Set(subprocessTest)),
      (testReqRespUser, Set(subprocessTest, subprocessReqResp)),
    )

    forAll(testingData) { (user: LoggedUser, expected: Set[ProcessWithJson] ) =>
      val result = dBProcessService.getSubProcesses(None)(user).futureValue
      val subprocessDetails = expected.map(convertBasicProcessToSubprocessDetails)
      result shouldBe subprocessDetails
    }
  }

  private def convertBasicProcessToSubprocessDetails(process: ProcessWithJson) =
    SubprocessDetails(ProcessConverter.fromDisplayable(process.json), process.processCategory)

  private def createDbProcessService[T: ProcessShapeFetchStrategy](processes: List[BaseProcessDetails[T]] = Nil): DBProcessService =
    new DBProcessService(
      managerActor = TestFactory.newDummyManagerActor(),
      requestTimeLimit = Duration.ofMinutes(1),
      newProcessPreparer = TestFactory.createNewProcessPreparer(),
      processCategoryService = processCategoryService,
      processResolving = TestFactory.processResolving,
      repositoryManager = TestFactory.newDummyRepositoryManager(),
      fetchingProcessRepository = new MockFetchingProcessRepository(processes),
      processActionRepository = TestFactory.newDummyActionRepository(),
      processRepository = TestFactory.newDummyWriteProcessRepository()
    )
}
