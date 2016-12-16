import com.google.cloud.bigtable.hbase.BigtableConfiguration

import org.apache.spark.rdd.NewHadoopRDD
import org.apache.hadoop.hbase.{
  HBaseConfiguration, HConstants, HTableDescriptor,
  HColumnDescriptor, TableName}
import org.apache.hadoop.hbase.client.{
  Connection, ConnectionFactory, Put,
  Result, RetriesExhaustedWithDetailsException, Table}
import org.apache.hadoop.hbase.io.ImmutableBytesWritable
import org.apache.hadoop.hbase.mapreduce.{TableInputFormat, TableOutputFormat}
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.mapreduce._
import java.lang.Exception
// Core Spark functionalities
import org.apache.spark._
// Implicit conversion between Java list and Scala list
import scala.collection.JavaConversions._

/** Word count in Spark
  */
object WordCount {
  val COLUMN_FAMILY = "cf"
  val COLUMN_FAMILY_BYTES = Bytes.toBytes(COLUMN_FAMILY)
  val COLUMN_NAME_BYTES = Bytes.toBytes("Count")

  def main(args: Array[String]) {
    if (args.length < 2) {
      throw new Exception("Please enter input file path, "
        + "output table name, and expected count as arguments")
    }
    val file = args(0) //file path
    val name = args(1) //output table name
    val sc = new SparkContext()

    var conf = BigtableConfiguration.configure(
        "google.com:hadoop-cloud-dev", "pc-test")
    conf.set(TableInputFormat.INPUT_TABLE, name)
    conf.set(TableOutputFormat.OUTPUT_TABLE, name)
    conf.setInt(HConstants.HBASE_CLIENT_OPERATION_TIMEOUT, 60000)
    conf.set(
        "hbase.client.connection.impl",
        "com.google.cloud.bigtable.hbase1_2.BigtableConnection")
    val job = new Job(conf)
    job.setOutputFormatClass(classOf[TableOutputFormat[ImmutableBytesWritable]])
    conf = job.getConfiguration()

    // create new table if doesn't already exist
    val tableName = TableName.valueOf(name)
    val conn = BigtableConfiguration.connect(conf)
    try {
      val admin = conn.getAdmin()
      if (!admin.tableExists(tableName)) {
        val tableDescriptor = new HTableDescriptor(tableName)
        tableDescriptor.addFamily(
          new HColumnDescriptor(COLUMN_FAMILY))
        admin.createTable(tableDescriptor)
      }
      admin.close()
    } finally {
      conn.close()
    }

    val wordCounts = sc.textFile(file)
        .flatMap(line => line.split(" "))
        .map(word => (word, 1))
        .reduceByKey(_ + _)
        .map({
            case (word, count) =>
              (null, new Put(Bytes.toBytes(word))
                  .addColumn(COLUMN_FAMILY_BYTES, COLUMN_NAME_BYTES, Bytes.toBytes(count)))})
    wordCounts.saveAsNewAPIHadoopDataset(conf)

    //validate table count
    val hBaseRDD = sc.newAPIHadoopRDD(
      conf,
      classOf[TableInputFormat],
      classOf[ImmutableBytesWritable],
      classOf[Result])
    val pairs = hBaseRDD
        .map({
            case (wordBytesWritable, countResult) =>
              (Bytes.toString(wordBytesWritable.get()), Bytes.toInt(countResult.value()))})
        .take(5)
    pairs.foreach(println)

    //cleanup
    val connCleanup = BigtableConfiguration.connect(conf)
    try {
      val admin = connCleanup.getAdmin()
      admin.deleteTable(tableName)
      admin.close()
    } finally {
      connCleanup.close()
    }
  }
}
