package com.tignear.pfa
import cats.syntax.all.*
import io.circe.generic.auto.*
import org.http4s.*
import org.http4s.server.Router
import sttp.tapir.PublicEndpoint
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.ztapir.*
import zio.interop.catz.*
import zio.{Console, ExitCode, IO, Layer, RIO, URIO, ZIO, ZIOAppDefault, ZLayer}
import sttp.tapir.Endpoint
import org.http4s.ember.server.EmberServerBuilder

object HelloWorld extends ZIOAppDefault:
  case class Pet(species: String, url: String)
  trait PetService {
    def find(id: Int): ZIO[Any, String, Pet]
  }

  object PetService {
    def find(id: Int): ZIO[PetService, String, Pet] =
      ZIO.environmentWithZIO(_.get.find(id))

    val live: Layer[String, PetService] = ZLayer.succeed {
      new PetService {
        override def find(petId: Int): IO[String, Pet] = {
          Console
            .printLine(s"Got request for pet: $petId")
            .mapError(_.getMessage) zipRight {
            if (petId == 35) {
              ZIO.succeed(
                Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir")
              )
            } else {
              ZIO.fail("Unknown pet id")
            }
          }
        }
      }
    }
  }
  // Sample endpoint, with the logic implemented directly using .toRoutes
  val petEndpoint: Endpoint[Unit, Int, String, Pet, Any] =
    endpoint.get
      .in("pet" / path[Int]("petId"))
      .errorOut(stringBody)
      .out(jsonBody[Pet])
  val petServerEndpoint: ZServerEndpoint[Any, Any] = petEndpoint.zServerLogic {
    petId =>
      if (petId == 35) {
        ZIO.succeed(
          Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir")
        )
      } else {
        ZIO.fail("Unknown pet id")
      }
  }
  val petServerRoutes: HttpRoutes[RIO[PetService, _]] =
    ZHttp4sServerInterpreter().from(petServerEndpoint).toRoutes
  val rootEndpoint: Endpoint[Unit, Unit, String, String, Any] =
    endpoint.get
      .in("")
      .errorOut(stringBody)
      .out(stringBody)

  val rootServerEndpoint: ZServerEndpoint[Any, Any] =
    rootEndpoint.zServerLogic { _ =>
      ZIO.succeed("Hello World!")
    }
  val serverRoutes: HttpRoutes[RIO[PetService, _]] =
    ZHttp4sServerInterpreter()
      .from(
        List(
          petServerEndpoint.widen[PetService],
          rootServerEndpoint.widen[PetService]
        )
      )
      .toRoutes
  val serve: ZIO[PetService, Throwable, Unit] =
    EmberServerBuilder
      .default[RIO[PetService, _]]
      .withHttpApp(Router("/" -> serverRoutes).orNotFound)
      .build
      .useForever

  override def run: URIO[Any, ExitCode] =
    serve.provideLayer(PetService.live).exitCode
