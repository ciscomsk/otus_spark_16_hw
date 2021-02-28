import org.apache.log4j.{Level, Logger}
import org.apache.spark.SparkContext
import org.apache.spark.sql.SparkSession

trait SparkSessionWrapper {
  lazy val spark: SparkSession = SparkSession
    .builder
    .appName("Structured streaming ML")
    .config("spark.master", "local[*]")
    .getOrCreate

  val sc: SparkContext = spark.sparkContext

  Logger.getLogger("org").setLevel(Level.OFF)
}