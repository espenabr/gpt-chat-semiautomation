package xf

import xf.gpt.GptApiClient
import cats.effect.Concurrent
import cats.implicits.*
import xf.gpt.GptApiClient.Common.Role.{Assistant, User}
import xf.gpt.GptApiClient.Request.Message
import xf.gpt.GptApiClient.Response.FinishReason.Choice.StopChoice
import xf.gpt.GptApiClient.Response.FinishReason.CompletionResponse
import xf.model.{ChatResponse, InteractionHandler, MessageExchange, SimpleChatResponse}

class InteractionClient[F[_]: Concurrent](gptApiClient: GptApiClient[F]) {

  def chat[A, B](
      requestValue: A,
      handler: InteractionHandler[A, B],
      history: List[MessageExchange] = List.empty
  ): F[ChatResponse[B]] = {
    val prompt =
      s"""${handler.objective}
         |
         |${handler.render(requestValue)}
         |
         |${handler.responseFormatDescription(requestValue)}""".stripMargin

    plainTextChat(prompt, history).map { response =>
      ChatResponse(
        handler.parse(requestValue, response.message),
        response.message,
        history :+ MessageExchange(prompt, response.message)
      )
    }
  }

  def plainTextChat(message: String, history: List[MessageExchange] = List.empty): F[SimpleChatResponse] = {
    val messages = appendToHistory(history, message)
    gptApiClient.chatCompletions(messages).map { response =>
      val reply = latestMessage(response)
      SimpleChatResponse(reply, history :+ MessageExchange(message, reply))
    }
  }

  private def appendToHistory(history: List[MessageExchange], prompt: String): List[Message] =
    history.flatMap { m => Message(User, m.message) :: Message(Assistant, m.reply) :: Nil } :+ Message(User, prompt)

  private def latestMessage(response: CompletionResponse) = response.choices.last match
    case StopChoice(_, message, _) => message.content
    case _                         => ""

}
