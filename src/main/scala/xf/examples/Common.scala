package xf.examples

import cats.effect.{IO, Resource}
import xf.TangibleClient
import xf.gpt.GptApiClient
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import xf.gpt.GptApiClient.Common.Message.ContentMessage
import xf.gpt.GptApiClient.Common.Role.User

object Common:

  implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]

  def createTangibleClient(
      client: Client[IO],
      apiKey: String
  ): TangibleClient[IO] =
    new TangibleClient[IO](new GptApiClient[IO](client, apiKey))

  def extractKey(): String =
    sys.env.get("GPT_CHAT_SEMIAUTOMATION_API_KEY") match
      case Some(key) => key
      case None =>
        println("OpenAPI key must be provided as argument!")
        ""

  def msg(content: String) =
    ContentMessage(User, content)

  val clientResource: Resource[IO, Client[IO]] = EmberClientBuilder
    .default[IO]
    .build
