package xf.interactionhandlers

import xf.model.InteractionHandler

object RecommendAuthors {

  case class FavoriteAuthors(authors: List[String])

  case class AuthorStrengths(strengths: List[String])

  val strengthsBasedOnAuthorsHandler = new InteractionHandler[FavoriteAuthors, AuthorStrengths](
    "Based on these authors, I want a list of their different unique skills. I want the top three biggest strengths of each author combined into one list.",
    _.authors.map(a => s"- $a").mkString("\n"),
    _ => s"""One specific strength on each line. No prefixed index or other character to indicate item in a list.
             |I want nothing else than this list.""".stripMargin,
    (_, s) => Some(AuthorStrengths(s.split("\n").toList.filter(_.nonEmpty)))
  )

  case class FavoriteStrengths(strengths: List[String])

  val recommendedAuthorsBasedOnFavoriteStrengths = new InteractionHandler[FavoriteStrengths, String](
    "Based on these preferred strengths and my favorite authors, I want you to recommend other authors I might like, and also tell me why.",
    _.strengths.map(s => s"- $s").mkString("\n"),
    _ => "",
    (_, s) => Some(s)
  )

}
