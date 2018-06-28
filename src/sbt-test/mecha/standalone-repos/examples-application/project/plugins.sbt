
resolvers += "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository"

sys.props.get("plugin.version") match {
  case Some(x) => addSbtPlugin("com.storm-enroute" % "mecha" % x)
  case _ => sys.error("""|The system property 'plugin.version' is not defined.
                         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
}
