
val maybeCredentials = for {
  username <- Option(System.getenv().get("SONATYPE_USER"))
  password <- Option(System.getenv().get("SONATYPE_PASS"))
} yield Credentials(
  "Sonatype Nexus Repository Manager",
  "oss.sonatype.org",
  username,
  password
)

credentials ++= maybeCredentials.toSeq
