package xf.gpt

import org.http4s.implicits.uri
import org.http4s.{AuthScheme, Credentials, Entity, EntityDecoder, EntityEncoder, Headers, MediaType, Method, Request}
import org.http4s.client.Client
import org.http4s.headers.{`Content-Type`, Authorization}
import org.http4s.circe.*
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.syntax.*
import cats.effect.Concurrent
import xf.gpt.GptApiClient.Common.Role
import xf.gpt.GptApiClient.Request.{CompletionRequest, Message, Tool}
import xf.gpt.GptApiClient.Request.Property.{EnumProperty, IntegerProperty, StringProperty}
import xf.gpt.GptApiClient.Response.FinishReason.CompletionResponse

class GptApiClient[F[_]: Concurrent](client: Client[F], val openAiKey: String) {

  private val entityEncoder: EntityEncoder[F, CompletionRequest] = jsonEncoderOf[CompletionRequest]

  given EntityDecoder[F, CompletionResponse] = jsonOf[F, CompletionResponse]

  def chatCompletions(messages: List[Message], tools: Option[List[Tool]] = None): F[CompletionResponse] =
    val body = CompletionRequest("gpt-4", messages, None) // gpt-4 also works

    val request = Request[F](
      method = Method.POST,
      uri = uri"https://api.openai.com/v1/chat/completions",
      headers = Headers(
        Authorization(Credentials.Token(AuthScheme.Bearer, openAiKey)),
        `Content-Type`(MediaType.application.json)
      ),
      entity = entityEncoder.toEntity(body)
    )

    client.expect[CompletionResponse](request)

}
object GptApiClient {

  object Common:
    enum Role:
      case System, User, Assistant, Function

    given Encoder[Role] = Encoder.encodeString.contramap {
      case Role.System    => "system"
      case Role.Assistant => "assistant"
      case Role.User      => "user"
      case Role.Function  => "function"
    }

    def parseRole(s: String): Either[String, Role] =
      s match {
        case "system"    => Right(Role.System)
        case "assistant" => Right(Role.Assistant)
        case "user"      => Right(Role.User)
        case "function"  => Right(Role.Function)
        case _           => Left(s"Could not decode $s as Role")
      }

    given Decoder[Role] = Decoder { c =>
      for {
        str  <- c.as[String]
        role <- parseRole(str).left.map(DecodingFailure(_, c.history))
      } yield role
    }

  // =======================================================================================

  object Request:
    case class Message(role: Common.Role, content: String)
    given Encoder[Message] = Encoder { m =>
      Json.obj(
        "role"    := m.role,
        "content" := m.content
      )
    }

    case class RequestFunction(name: String, description: Option[String], parameters: Parameters)
    object RequestFunction:
      given Encoder[RequestFunction] = Encoder { f =>
        Json.obj(
          "name"        := f.name,
          "description" := f.description,
          "parameters"  := f.parameters
        )
      }

    val propertyEncoder: Encoder[Property] = Encoder.instance {
      case StringProperty(description)      =>
        Json.obj(
          "type"        := "string",
          "description" := description
        )
      case EnumProperty(description, _enum) =>
        Json.obj(
          "type"        := "string",
          "description" := description,
          "num"         := _enum
        )
      case IntegerProperty(description)     =>
        Json.obj(
          "type"        := "integer",
          "description" := description
        )
    }

    given Encoder[Property] = propertyEncoder

    case class Tool(function: RequestFunction)
    object Tool:
      given Encoder[Tool] = Encoder { t =>
        Json.obj(
          "type"     := "function",
          "function" := t.function
        )
      }

    enum Property:
      case StringProperty(description: String)
      case EnumProperty(description: String, _enum: List[String])
      case IntegerProperty(description: String)

    case class Parameters(properties: Map[String, Property], required: List[String])
    object Parameters:
      given Encoder[Map[String, Property]] = Encoder.instance { map =>
        Json.obj(map.map { case (key, value) =>
          key -> value.asJson(propertyEncoder)
        }.toSeq: _*)
      }
      given Encoder[Parameters]            = Encoder { p =>
        Json.obj(
          "type"       := "object",
          "properties" := p.properties
        )
      }

    case class CompletionRequest(model: String, messages: List[Message], tools: Option[List[Tool]])
    object CompletionRequest:
      given Encoder[CompletionRequest] = Encoder { r =>
        Json.obj(
          "model"    := r.model,
          "messages" := r.messages,
          "tools"    := r.tools
        )
      }

  // =======================================================================================

  object Response:
    enum FinishReason:
      case Stop, ToolCalls
    object FinishReason:
      def toEnum(s: String): Option[FinishReason] =
        Option(s) collect {
          case "stop"       => Stop
          case "tool_calls" => ToolCalls
        }
      given Decoder[FinishReason]                 = Decoder.decodeString.map(s => FinishReason.toEnum(s).get)

      case class CompletionResponse(id: String, model: String, choices: List[Choice], usage: Usage)
      object CompletionResponse:
        given Decoder[CompletionResponse] = Decoder { c =>
          for {
            id      <- c.downField("id").as[String]
            model   <- c.downField("model").as[String]
            choices <- c.downField("choices").as[List[Choice]]
            usage   <- c.downField("usage").as[Usage]
          } yield CompletionResponse(id, model, choices, usage)
        }

      case class ToolCallFunction(name: String, arguments: String) // TODO Json must be decoded
      object ToolCallFunction:
        given Decoder[ToolCallFunction] = Decoder { c =>
          for
            name      <- c.downField("name").as[String]
            arguments <- c.downField("arguments").as[String]
          yield ToolCallFunction(name, arguments)
        }

      case class ToolCall(id: String, function: ToolCallFunction)
      object ToolCall:
        given Decoder[ToolCall] = Decoder { c =>
          for
            id       <- c.downField("id").as[String]
            function <- c.downField("function").as[ToolCallFunction]
          yield ToolCall(id, function)
        }

      case class ResponseMessage(role: Role, content: String)
      object ResponseMessage:
        given Decoder[ResponseMessage] = Decoder { c =>
          for
            role    <- c.downField("role").as[Role]
            content <- c.downField("content").as[String]
          yield ResponseMessage(role, content)
        }

      case class ToolCallsMessage(role: Role, toolCalls: List[ToolCall])
      object ToolCallsMessage:
        given Decoder[ToolCallsMessage] = Decoder { c =>
          for
            role      <- c.downField("role").as[Role]
            toolCalls <- c.downField("tool_calls").as[List[ToolCall]]
          yield ToolCallsMessage(role, toolCalls)
        }

      case class Usage(promptTokens: Int, completionTokens: Int, totalTokens: Int)
      object Usage {
        given Decoder[Usage] = Decoder { c =>
          for {
            promptTokens     <- c.downField("prompt_tokens").as[Int]
            completionTokens <- c.downField("completion_tokens").as[Int]
            totalTokens      <- c.downField("total_tokens").as[Int]
          } yield Usage(promptTokens, completionTokens, totalTokens)
        }
      }

      enum Choice:
        case StopChoice(index: Int, message: ResponseMessage, finishReason: FinishReason)
        case ToolCallsChoice(index: Int, message: ToolCallsMessage, finishReason: FinishReason)

      object Choice:
        given Decoder[Choice] = Decoder.instance { c =>
          c.get[FinishReason]("finish_reason").flatMap {
            case Stop      =>
              for
                index        <- c.downField("index").as[Int]
                message      <- c.downField("message").as[ResponseMessage]
                finishReason <- c.downField("finish_reason").as[FinishReason]
              yield StopChoice(index, message, finishReason)
            case ToolCalls =>
              for
                index        <- c.downField("index").as[Int]
                message      <- c.downField("message").as[ToolCallsMessage]
                finishReason <- c.downField("finish_reason").as[FinishReason]
              yield ToolCallsChoice(index, message, finishReason)
          }
        }

}
