package com.tencent.angel.spark.ml

import com.tencent.angel.conf.AngelConf
import com.tencent.angel.exception.AngelException
import com.tencent.angel.ml.core.conf.{MLCoreConf, SharedConf}
import com.tencent.angel.ml.math2.utils.RowType
import com.tencent.angel.spark.context.PSContext
import com.tencent.angel.spark.ml.core.{ArgsUtil, GraphModel, OfflineLearner}
import org.apache.log4j.PropertyConfigurator
import org.apache.spark.{SparkConf, SparkContext}

object NFMTest {

  def main(args: Array[String]): Unit = {
    PropertyConfigurator.configure("angel-ps/conf/log4j.properties")
    val params = ArgsUtil.parse(args)

    val input = params.getOrElse("input", "./spark-on-angel/mllib/src/test/data/census.train")
    val modelInput = params.getOrElse("model", "")
    val modelOutput = params.getOrElse("output", "")
    val actionType = params.getOrElse("actionType", "train")

    // build SharedConf with params
    SharedConf.get()
    SharedConf.addMap(params)
    SharedConf.get().set(MLCoreConf.ML_MODEL_TYPE, RowType.T_FLOAT_DENSE.toString)
    SharedConf.get().setInt(MLCoreConf.ML_FEATURE_INDEX_RANGE, 148)
    SharedConf.get().setDouble(MLCoreConf.ML_LEARN_RATE, 0.5)
    SharedConf.get().set(MLCoreConf.ML_DATA_INPUT_FORMAT, "dummy")
    SharedConf.get().setInt(MLCoreConf.ML_EPOCH_NUM, 200)
    SharedConf.get().setDouble(MLCoreConf.ML_VALIDATE_RATIO, 0.0)
    SharedConf.get().setDouble(MLCoreConf.ML_REG_L2, 0.0)
    SharedConf.get().setDouble(MLCoreConf.ML_BATCH_SAMPLE_RATIO, 0.2)
    SharedConf.get().setInt(AngelConf.ANGEL_PS_BACKUP_INTERVAL_MS, 1000000000)

    // Set NFM algorithm parameters
    val angelConfFile = "./angel-ps/mllib/src/test/jsons/nfm.json"
    SharedConf.get().set(AngelConf.ANGEL_ML_CONF, angelConfFile)

    val className = "com.tencent.angel.spark.ml.classification.NeuralFM"
    val model = GraphModel(className)
    val learner = new OfflineLearner()

    // load data
    val conf = new SparkConf()
    conf.setMaster("local")
    conf.setAppName("NFM Test")
    conf.set("spark.ps.model", "LOCAL")
    conf.set("spark.ps.jars", "")
    conf.set("spark.ps.instances", "1")
    conf.set("spark.ps.cores", "1")
    conf.set("spark.ps.log.level", "INFO")

    val sc = new SparkContext(conf)
    val dim = SharedConf.indexRange.toInt


    PSContext.getOrCreate(sc)

    actionType match {
      case "train" =>
        learner.train(input, modelOutput, modelInput, dim, model)

      case "com/tencent/angel/ml/predict" =>
        learner.predict(input, modelOutput, modelInput, dim, model)
      case _ =>
        throw new AngelException("actionType should be train or predict")
    }

    PSContext.stop()
    sc.stop()
  }
}
