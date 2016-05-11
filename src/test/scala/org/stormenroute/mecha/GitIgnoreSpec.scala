package org.stormenroute.mecha



import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.core.Fragments
import org.stormenroute.mecha.GitIgnore._



@RunWith(value = classOf[JUnitRunner])
class GitIgnoreSpec extends Specification {
  val newRepo = "some-path"

  val MatchingBlacklist = Seq(
    "some-path",
    "/some-path",
    " some-path/ ",
    "some-*",
    "*-path",
    "*",
    "*-*",
    "**",
    "**/some-*",
    "**/some-path/**",
    "some-*/**",
    "**/some-*/**",
    "**/*-path/**"
  )

  val NonMatchingBlacklist = Seq(
    "some-other-path"
  )

  val MatchingWhitelist = Seq(
    "!some-path"
  )

  val NonMatchingWhitelist = Seq(
    "!some-other-path"
  )

  val ignoreFile1 = Seq(
    "some-*-path",
    " !some-other-path",
    "some-path/",
    "**/some-*",
    "**/some-path/**"
  )

  val ignoreFile2 = Seq(
    "some-*",
    " another-path",
    "*-path",
    "some-path",
    "!/some-path",
    "some-*/**",
    "**/some-*/**",
    "**/*-path/**"
  )

  val ignoreFile3 = Seq(
    "another-path",
    " !/some-other-path",
    "not-some-*/**",
    "**/some-*-path/**",
    "**/*-path-/**"
  )

  "Individual line matching to paths (either whitelist or blacklist)" >> {
    s"Path: '$newRepo'" >> {
      Fragments.foreach(MatchingWhitelist ++ MatchingBlacklist) {
        p => s"matches: '$p'" ! { Line(p).matches(newRepo) } ^ br
      }: Fragments

      Fragments.foreach(NonMatchingWhitelist ++ NonMatchingBlacklist) {
        p => s"doesn't match: '$p'" ! { !Line(p).matches(newRepo) } ^ br
      }: Fragments
    }
  }

  "Find all matches for a path given a list of patterns" >> {
    s"Find all 'blacklist' matches for '$newRepo'" >> {
      blacklists(newRepo,
        (MatchingBlacklist ++ NonMatchingBlacklist).map(Line(_))) must_===
          MatchingBlacklist.map(Line.apply).collect { case bl: BlacklistPattern => bl }
    }

    s"Find all 'whitelist' matches for '$newRepo'" >> {
      whitelists(newRepo,
        (MatchingWhitelist ++ NonMatchingWhitelist).map(Line(_))) must_===
          MatchingWhitelist.map(Line.apply).collect { case bl: WhitelistPattern => bl }
    }
  }

  "Find all matches for a path given ignore/exclude file(s)" >> {
    "Pattern not added because it is whitelisted" >> {
      val result = addIgnore(newRepo, Seq(), ignoreFile1 ++ ignoreFile2)
      result.isLeft && (
        result.left.get must_=== Whitelists(Seq(WhitelistPattern("/some-path")))
      )
    }

    "Pattern not added because it is already blacklisted" >> {
      val result = addIgnore(newRepo, Seq(), ignoreFile1)
      result.isLeft && (result.left.get must_===
        Blacklists(Seq(
          BlacklistPattern("some-path/"),
          BlacklistPattern("**/some-*"),
          BlacklistPattern("**/some-path/**"))))
    }

    "New pattern added to gitgnore/exclude" >> {
      val result = addIgnore(newRepo,
        Seq("existing-pattern", "another-pattern", "yet-another_pattern"), ignoreFile3)
      result.isRight && (result.right.get must_===
        Seq("existing-pattern", "another-pattern", "yet-another_pattern", "some-path"))
    }
  }
}
