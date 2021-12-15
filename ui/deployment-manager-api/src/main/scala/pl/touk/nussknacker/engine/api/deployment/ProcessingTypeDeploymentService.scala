package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion

import scala.concurrent.Future

trait ProcessingTypeDeploymentService {

  // shouldn't we pass also category as a filter?
  def getDeployedScenarios: Future[List[DeployedScenarioData]]

}

case class DeployedScenarioData(processVersion: ProcessVersion, deploymentData: DeploymentData, resolvedProcessDeploymentData: ProcessDeploymentData)

class ProcessingTypeDeploymentServiceStub(deployedScenarios: List[DeployedScenarioData]) extends ProcessingTypeDeploymentService {

  override def getDeployedScenarios: Future[List[DeployedScenarioData]] = Future.successful(deployedScenarios)

}