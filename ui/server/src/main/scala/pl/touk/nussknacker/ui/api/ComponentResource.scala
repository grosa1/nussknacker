package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.restmodel.component.ComponentProcess
import pl.touk.nussknacker.ui.api.EspErrorToHttp.toResponseEither
import pl.touk.nussknacker.ui.component.ComponentService
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

class ComponentResource(componentService: ComponentService)(implicit val ec: ExecutionContext, mat: Materializer)
  extends Directives with FailFastCirceSupport with RouteWithUser {
  override def securedRoute(implicit user: LoggedUser): Route =
    encodeResponse {
      path("components") {
        get {
          complete {
            componentService.getComponentsList(user)
          }
        }
      } ~ path("component" / Segment / "processes") { componentId =>
        get {
          complete {
            componentService
              .getComponentProcesses(ComponentId.create(componentId))
              .map(toResponseEither[List[ComponentProcess]])
          }
        }
      }
    }
}
