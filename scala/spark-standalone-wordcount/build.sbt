name := "WordCount"

version := "0.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "2.0.1" % "provided",
  "org.apache.hadoop" % "hadoop-common" % "2.7.3" % "provided",
  "com.google.cloud.bigtable" % "bigtable-hbase-1.2" % "0.9.4",
  "org.apache.hbase" % "hbase-client" % "1.2.2",
  "org.apache.hbase" % "hbase-server" % "1.2.2",
  "org.apache.hbase" % "hbase-common" % "1.2.2",
  "org.apache.hbase" % "hbase-annotations" % "1.2.2").map(
    a => a.excludeAll(
      ExclusionRule(organization = "org.mortbay.jetty")
    ))

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "MANIFEST.MF" ) => MergeStrategy.discard
  case _ => MergeStrategy.first
}
