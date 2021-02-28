import org.apache.spark.ml.PipelineModel
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions.{concat_ws, from_csv, when}
import org.apache.spark.sql.types.{DoubleType, StringType, StructField, StructType}

object StructuredStreamingClassification extends App with SparkSessionWrapper {
  import spark.implicits._

  val cols: Seq[String] = Seq("sepal_length", "sepal_width", "petal_length", "petal_width")

  val model = PipelineModel.load("src/main/resources/model/")

  val schema: StructType = cols.foldLeft(StructType(Nil)){ (acc, col) =>
    acc.add(StructField(col, DoubleType, nullable = true))
  }

  val va: VectorAssembler = new VectorAssembler()
    .setInputCols(cols.toArray)
    .setOutputCol("features")

  val dataDF: DataFrame = spark
    .readStream
    .format("kafka")
    .option("kafka.bootstrap.servers", "localhost:29092")
    .option("failOnDataLoss", "false")
    .option("subscribe", "input")
    .load()
    .select($"value".cast(StringType))
    .withColumn("struct", from_csv($"value", schema, Map("sep" -> ",")))
    .withColumn("sepal_length", $"struct".getField("sepal_length"))
    .withColumn("sepal_width", $"struct".getField("sepal_width"))
    .withColumn("petal_length", $"struct".getField("petal_length"))
    .withColumn("petal_width", $"struct".getField("petal_width"))
    .drop("value", "struct")

  val data: DataFrame = va.transform(dataDF)

  val prediction: DataFrame = model.transform(data)

  val query = prediction
    .withColumn(
      "predictedLabel",
      when($"prediction" === 0.0 , "setosa")
        .when($"prediction" === 1.0 , "versicolor")
        .when($"prediction" === 2.0 , "virginica")
        .otherwise("unknown")
    )
    .select(
      $"predictedLabel".as("key"),
      concat_ws(",", $"sepal_length", $"sepal_width", $"petal_length", $"petal_width", $"predictedLabel").as("value")
    )
    .writeStream
    .outputMode("append")
    .format("kafka")
    .option("kafka.bootstrap.servers", "localhost:29092")
    .option("checkpointLocation", "src/main/resources/checkpoint/")
    .option("topic", "prediction")
    .start()

  query.awaitTermination
}
