package xf.examples

import cats.effect.{ExitCode, IO, IOApp}
import cats.effect.std.Console
import xf.examples.Common.{clientResource, createConversationClient, extractKey}
import xf.Input.{collectAnswers, prompt}
import xf.interactionhandlers.RequestQuestions.{requestQuizQuestions, QuizRequest}
import xf.interactionhandlers.RequestQuestions.QuestionType.{SingleChoiceQuestions, YesNoQuestions}
import xf.interactionhandlers.AnswerQuestions.answerQuestionsHandler
import xf.interactionhandlers.RequestQuestions.Difficulty.Medium

object Quiz extends IOApp {

  def run(args: List[String]): IO[ExitCode] = clientResource
    .use { client =>
      val interactions = createConversationClient(client, extractKey(args))
      for {
        topic     <- prompt("Quiz topic")
        questions <-
          interactions.chat(QuizRequest(topic, Medium, SingleChoiceQuestions(Some(3)), Some(6)), requestQuizQuestions)
        answers   <- collectAnswers(questions.value.get)
        result    <- interactions.chat(answers, answerQuestionsHandler, questions.history)
        _         <- Console[IO].println(s"${result.value.get}")
      } yield ExitCode.Success
    }

}