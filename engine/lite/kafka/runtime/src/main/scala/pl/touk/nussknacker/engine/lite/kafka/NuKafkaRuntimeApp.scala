package pl.touk.nussknacker.engine.lite.kafka

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader
import org.apache.commons.io.FileUtils
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.{HttpConfig, RunnableScenarioInterpreterFactory}
import pl.touk.nussknacker.engine.marshall.ScenarioParser
import pl.touk.nussknacker.engine.util.config.ConfigFactoryExt
import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
import pl.touk.nussknacker.engine.util.{JavaClassVersionChecker, SLF4JBridgeHandlerRegistrar}

import java.nio.file.Path
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

// TODO: get rid of kafka specific things: class name, LiteKafkaJobData
object NuKafkaRuntimeApp extends App with LazyLogging {

  JavaClassVersionChecker.check()
  SLF4JBridgeHandlerRegistrar.register()

  val (scenarioFileLocation, deploymentConfigLocation) = parseArgs
  val scenario = parseScenario(scenarioFileLocation)
  val liteKafkaJobData = parseDeploymentConfig(deploymentConfigLocation)
  val runtimeConfig = ConfigFactory.load(ConfigFactoryExt.parseUnresolved(classLoader = getClass.getClassLoader))

  val httpConfig = runtimeConfig.as[HttpConfig]("http")

  implicit val system = ActorSystem("nu-lite-runtime", runtimeConfig)
  import system.dispatcher

  // Because actor system creates non-daemon threads, all exceptions from current thread will be suppressed and process
  // will be still alive even if something fail (like scenarioInterpreter creation)
  val exitCode = try {
    runAfterActorSystemCreation()
    0
  } catch {
    case NonFatal(ex) =>
      logger.error("Exception during runtime execution", ex)
      1
  } finally {
    Await.result(system.terminate(), 5.seconds)
  }
  System.exit(exitCode)

  private val akkaHttpStartAndCloseTimeout = 10 seconds

  private def runAfterActorSystemCreation(): Unit = {
    val scenarioInterpreter = RunnableScenarioInterpreterFactory.prepareScenarioInterpreter(scenario, runtimeConfig, liteKafkaJobData, system)



    val healthCheckProvider = new HealthCheckRoutesProvider(system, scenarioInterpreter)

    val httpServer = Http().newServerAt(interface = httpConfig.interface, port = httpConfig.port)

    val runFuture = scenarioInterpreter.run()
    val healthCheckRoutes = healthCheckProvider.routes()
    val routes = Directives.concat(scenarioInterpreter.routes().toList ::: healthCheckRoutes :: Nil: _*)

    val boundRoutesFuture = Future {
      Await.result(httpServer.bind(routes), akkaHttpStartAndCloseTimeout)
    }

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        logger.info("Closing RunnableScenarioInterpreter")
        scenarioInterpreter.close()
        Await.ready(boundRoutesFuture.flatMap(_.terminate(akkaHttpStartAndCloseTimeout)), akkaHttpStartAndCloseTimeout)
      }
    })
    boundRoutesFuture.onComplete {
      case Failure(exception) => logger.error(s"Failed to start http server within $akkaHttpStartAndCloseTimeout", exception)
      case Success(_) => logger.info(s"Http server started on ${httpConfig.interface}:${httpConfig.port}")
    }

    Await.result(Future.sequence(List(runFuture, boundRoutesFuture)), Duration.Inf)
    logger.info("Closing application NuKafkaRuntimeApp")
  }


  private def parseArgs: (Path, Path) = {
    if (args.length < 1) {
      missingArgumentError("scenario_file_location")
    } else if (args.length < 2) {
      missingArgumentError("deployment_config_location")
    }
    (Path.of(args(0)), Path.of(args(1)))
  }

  private def missingArgumentError(argumentName: String): Unit = {
    System.err.println(s"Missing $argumentName argument!")
    System.err.println("")
    System.err.println("Usage: ./run.sh scenario_file_location.json deployment_config_location.conf")
    sys.exit(1)
  }

  private def parseScenario(location: Path): EspProcess = {
    val scenarioString = FileUtils.readFileToString(location.toFile)
    logger.info(s"Running scenario: $scenarioString")

    val parsedScenario = ScenarioParser.parse(scenarioString)
    parsedScenario.valueOr { err =>
      System.err.println("Scenario file is not a valid json")
      System.err.println(s"Errors found: ${err.toList.mkString(", ")}")
      sys.exit(2)
    }
  }

  private def parseDeploymentConfig(path: Path): LiteKafkaJobData = {
    ConfigFactory.parseFile(path.toFile).as[LiteKafkaJobData]
  }

}